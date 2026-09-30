package se.partee71.reseapoteket.data.common

import kotlin.time.Instant
import se.partee71.reseapoteket.core.model.Identified
import se.partee71.reseapoteket.core.model.Sortable
import se.partee71.reseapoteket.core.schema.DocCodec
import se.partee71.reseapoteket.core.schema.Doc
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.core.schema.SchemaMigrator

// Regler för läsning och skrivning som gäller alla samlingar – används av både
// FirestoreCollection och FakeCollection, så att de bevisligen beter sig lika.

/**
 * Före skrivning: `updatedAt` sätts till [now] när modellen har fältet, och ett `createdAt`
 * utan värde skrivs inte – annars skulle en merge radera det lagrade värdet.
 */
fun prepareForWrite(encoded: Doc, now: Instant): Doc = buildMap {
    for ((key, value) in encoded) {
        when {
            key == "updatedAt" -> put(key, now)
            key == "createdAt" && value == null -> Unit
            else -> put(key, value)
        }
    }
}

/** Ett migreringssteg-anrop; i appen alltid [SchemaMigrator.migrate], i tester utbytbart. */
typealias Migrate = (from: Int, collection: String, doc: Doc) -> Doc

/**
 * Läsning: dokument i ett äldre format lyfts av [migrate] innan de avkodas; nyare format
 * avkodas som de är (appen är då skrivskyddad, se [writeBlocker]). Okänd version läses som
 * nuvarande.
 */
fun <T> readDocument(
    codec: DocCodec<T>,
    collection: String,
    version: Int?,
    id: String,
    raw: Doc,
    migrate: Migrate = SchemaMigrator::migrate,
): T {
    val from = version ?: Schema.CURRENT_VERSION
    val doc = if (Schema.isNewerThanApp(from) || from == Schema.CURRENT_VERSION) raw else migrate(from, collection, raw)
    return codec.decode(id, doc)
}

/**
 * Ordningen i en lista: på dokument-ID (som Firestore utan `orderBy`), sedan på `sortOrder`
 * för `Sortable` – deterministiskt och likadant i riktig och fejkad samling.
 */
fun <T : Identified> sortForList(items: List<T>): List<T> {
    val byId = items.sortedBy { it.id }
    @Suppress("UNCHECKED_CAST")
    return if (byId.firstOrNull() is Sortable) byId.sortedWith(Sortable.ORDER as Comparator<T>) else byId
}
