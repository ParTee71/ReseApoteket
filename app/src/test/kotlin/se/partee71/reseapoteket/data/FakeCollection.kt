package se.partee71.reseapoteket.data

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import se.partee71.reseapoteket.core.model.Identified
import se.partee71.reseapoteket.core.schema.Doc
import se.partee71.reseapoteket.core.schema.DocCodec
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.EntityCollection
import se.partee71.reseapoteket.data.common.HouseholdScope
import se.partee71.reseapoteket.data.common.currentVersion
import se.partee71.reseapoteket.data.common.fieldsForUpdate
import se.partee71.reseapoteket.data.common.prepareForWrite
import se.partee71.reseapoteket.data.common.readDocument
import se.partee71.reseapoteket.data.common.sortForList
import se.partee71.reseapoteket.data.common.suspendRunCatching
import se.partee71.reseapoteket.data.common.writeBlocker
import se.partee71.reseapoteket.data.firestore.CollectionTable

/**
 * Minnesbaserad databas med Firestores semantik för det appen använder: merge är djup för
 * nästlade maps, `null` lagras som `null`, heltal lagras som `Long`. Som `firestore.rules`
 * skapas ett dokument i en undersamling (recept, packrad) bara under en förälder som finns.
 */
class FakeStore {
    val documents = MutableStateFlow<Map<String, Map<String, Doc>>>(emptyMap())

    fun set(path: String, id: String, doc: Doc, merge: Boolean) = documents.update { all ->
        val collection = all[path].orEmpty()
        check(id in collection || parentExists(all, path)) { "Föräldern till $path finns inte (firestore.rules)" }
        val stored = firestoreValues(doc)
        val merged = if (merge) deepMerge(collection[id].orEmpty(), stored) else stored
        all + (path to collection + (id to merged))
    }

    /**
     * Som Firestores `update` i en batch: varje fält ersätts helt, även en map – inget slås ihop
     * på djupet. Allt eller inget: finns ett dokument inte avvisar servern hela batchen.
     */
    fun patch(path: String, patches: Map<String, Doc>) = documents.update { all ->
        val collection = all[path].orEmpty()
        if (!collection.keys.containsAll(patches.keys)) return@update all
        all + (path to collection + patches.mapValues { (id, fields) -> collection.getValue(id) + firestoreValues(fields) })
    }

    fun delete(path: String, id: String) = documents.update { all -> all + (path to all[path].orEmpty() - id) }

    fun read(path: String, id: String): Doc? = documents.value[path]?.get(id)

    /** `households/{hid}/trips/{tid}/packingItems` kräver `trips/{tid}`; en samling direkt under hushållet kräver inget. */
    private fun parentExists(all: Map<String, Map<String, Doc>>, path: String): Boolean {
        val segments = path.split('/')
        if (segments.size <= 3) return true
        return all[segments.dropLast(2).joinToString("/")]?.containsKey(segments[segments.size - 2]) == true
    }

    fun collection(path: String): Flow<Map<String, Doc>> = documents.map { it[path].orEmpty() }.distinctUntilChanged()

    private fun deepMerge(old: Doc, new: Doc): Doc = old + new.mapValues { (key, value) ->
        val previous = old[key]
        if (value is Map<*, *> && previous is Map<*, *>) deepMerge(asDoc(previous), asDoc(value)) else value
    }

    @Suppress("UNCHECKED_CAST")
    private fun asDoc(value: Map<*, *>) = value as Doc

    private fun firestoreValues(doc: Doc): Doc = doc.mapValues { (_, v) -> firestoreValue(v) }

    private fun firestoreValue(value: Any?): Any? = when (value) {
        is Int -> value.toLong()
        is Map<*, *> -> value.mapValues { firestoreValue(it.value) }
        is List<*> -> value.map(::firestoreValue)
        else -> value
    }
}

/** `EntityCollection` i minnet – samma regler som `FirestoreCollection` (bevisat av `CollectionContract`). */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeCollection<T : Identified>(
    private val store: FakeStore,
    private val scope: HouseholdScope,
    private val clock: Clock,
    private val codec: DocCodec<T>,
    private val name: String,
    private val path: (householdId: String?) -> String,
) : EntityCollection<T> {

    override fun observe(): Flow<List<T>> = scope.householdId.flatMapLatest { hid ->
        val p = pathOrNull(hid) ?: return@flatMapLatest emptyFlow()
        store.collection(p).map { docs -> sortForList(docs.map { (id, doc) -> decode(id, doc) }) }
    }

    override fun observe(id: String): Flow<T?> = scope.householdId.flatMapLatest { hid ->
        val p = pathOrNull(hid) ?: return@flatMapLatest emptyFlow()
        store.collection(p).map { docs -> docs[id]?.let { decode(id, it) } }
    }

    override suspend fun get(id: String) = run { store.read(path(), id)?.let { decode(id, it) } }

    override suspend fun getAll() = run { store.documents.value[path()].orEmpty().map { (id, doc) -> decode(id, doc) } }

    override suspend fun upsert(item: T) = write { store.set(path(), item.id, encode(item), merge = true) }

    override suspend fun delete(id: String) = write { store.delete(path(), id) }

    /** Som Firestores `update`: ett dokument som inte finns skapas inte. */
    override suspend fun setArchived(id: String, archived: Boolean) = write {
        val p = path()
        if (store.read(p, id) != null) store.set(p, id, mapOf("archived" to archived), merge = true)
    }

    override suspend fun updateAll(items: List<T>, fields: Set<String>) = write {
        store.patch(path(), items.associate { it.id to fieldsForUpdate(encode(it), fields) })
    }

    override suspend fun batch(upserts: List<T>, deletes: List<String>) = write {
        val p = path()
        upserts.forEach { store.set(p, it.id, encode(it), merge = true) }
        deletes.forEach { store.delete(p, it) }
    }

    override fun newId(): String = "id-" + nextId++

    private fun encode(item: T) = prepareForWrite(codec.encode(item), clock.now())

    private fun decode(id: String, doc: Doc) = readDocument(codec, name, scope.currentVersion(), id, doc)

    private fun path(): String = path(scope.householdId.value)

    private fun pathOrNull(hid: String?) = runCatching { path(hid) }.getOrNull()

    private suspend fun <R> run(block: suspend () -> R): Result<R> = suspendRunCatching({ DataError.Unknown }) { block() }

    private suspend fun write(block: suspend () -> Unit): Result<Unit> = run {
        scope.writeBlocker()?.let { throw it }
        block()
    }

    private companion object {
        var nextId = 0
    }
}

/** Samma tabell som appen (`CollectionTable`), men med [FakeCollection]. */
class FakeCollectionFactory(
    val store: FakeStore = FakeStore(),
    val scope: HouseholdScope = TestHouseholdScope(),
    private val clock: Clock = FixedClock(),
) : CollectionTable() {
    override fun <T : Identified> create(codec: DocCodec<T>, name: String, path: (householdId: String?) -> String) =
        FakeCollection(store, scope, clock, codec, name, path)
}
