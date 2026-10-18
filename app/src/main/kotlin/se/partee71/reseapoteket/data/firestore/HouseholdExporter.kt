package se.partee71.reseapoteket.data.firestore

import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import se.partee71.reseapoteket.core.schema.ExportFormat
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.HouseholdScope
import se.partee71.reseapoteket.data.common.suspendRunCatching
import se.partee71.reseapoteket.data.export.HouseholdExport
import se.partee71.reseapoteket.di.DefaultDispatcher

/**
 * Går igenom det aktiva hushållet längs alla samlingar i [Paths] – samma som
 * `tools/db/lib/collections.mjs` (`collections.test.mjs` håller dem lika) – och skriver filen med
 * [ExportFormat]. Appen kan inte lista undersamlingar som `tools/db` gör, så ett dokument under
 * en förälder som saknar eget dokument (recept under en person utan dokument) kommer bara med i
 * veckobackupen; appen själv visar aldrig sådana.
 */
class HouseholdExporter @Inject constructor(
    private val raw: RawDocuments,
    private val scope: HouseholdScope,
    private val clock: Clock,
    @param:DefaultDispatcher private val computation: CoroutineDispatcher
) : HouseholdExport {

    override suspend fun export(): Result<String> = suspendRunCatching(::firestoreError) {
        val hid = scope.householdId.value ?: throw DataError.NoHousehold
        raw.awaitPendingWrites()
        val (household, documents) = coroutineScope {
            val household = async { raw.document(Paths.household(hid)) ?: throw DataError.NotFound }
            val persons = async { read(Paths.persons(hid)) { personId -> read(Paths.regimens(hid, personId)) } }
            val familyItems = async { read(Paths.familyItems(hid)) }
            val trips = async { read(Paths.trips(hid)) { tripId -> read(Paths.packingItems(hid, tripId)) } }
            val stored = household.await()
            stored to listOf(ExportFormat.Document(Paths.household(hid), stored)) + persons.await() + familyItems.await() + trips.await()
        }
        // Ett stort hushåll ska inte byggas till text på huvudtråden.
        withContext(computation) { ExportFormat.encode(clock.now(), Schema.versionOf(household["schemaVersion"]), documents) }
    }

    /**
     * Dokumenten i [path] i ID-ordning, vart och ett följt av det som [children] läser under det;
     * undersamlingarna läses samtidigt.
     */
    private suspend fun read(
        path: String,
        children: (suspend (id: String) -> List<ExportFormat.Document>)? = null
    ): List<ExportFormat.Document> = coroutineScope {
        val documents = raw.collection(path).toSortedMap().map { (id, data) -> ExportFormat.Document("$path/$id", data) }
        if (children == null) return@coroutineScope documents
        documents.map { document -> async { listOf(document) + children(document.path.substringAfterLast('/')) } }.awaitAll().flatten()
    }
}
