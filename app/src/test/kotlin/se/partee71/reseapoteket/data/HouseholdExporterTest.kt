package se.partee71.reseapoteket.data

import java.io.File
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.schema.ExportFormat
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.dataError
import se.partee71.reseapoteket.data.firestore.HouseholdExporter

/**
 * "Exportera som JSON" (SET-5): appens export av ett hushåll är densamma som `tools/db export`
 * av det – samma sökvägar, samma värden, samma format – och kan därför läsas in med
 * `tools/db import`, vars rundtur testas mot samma testdata (`roundtrip.test.mjs`).
 */
class HouseholdExporterTest {

    /** tools/db:s testdata – alla samlingar och fälttyper, även okända fält. */
    private val fixture = ExportFormat.decode(File("../tools/db/test/fixtures/household.json").readText())
    private val store = FakeStore()
    private val scope = TestHouseholdScope(householdId = "hushall-test")
    private val clock = FixedClock()
    private val raw = FakeRawDocuments(store)
    private val exporter = HouseholdExporter(raw, scope, clock)

    private fun seed(documents: List<ExportFormat.Document>) {
        store.documents.value = documents.groupBy { it.path.substringBeforeLast('/') }
            .mapValues { (_, docs) -> docs.associate { it.path.substringAfterLast('/') to it.data } }
    }

    @Test
    fun `exporten innehåller hushållets alla dokument exakt som de ligger lagrade`() = runTest {
        seed(fixture)
        val text = exporter.export().getOrThrow()

        val exported = ExportFormat.decode(text)
        // Erik har recept men inget eget dokument: appen kan inte lista undersamlingar som
        // tools/db gör – sådant kommer bara med i veckobackupen (och syns aldrig i appen).
        val reachable = fixture.filterNot { it.path.contains("/persons/erik/") }
        assertEquals(reachable.sortedBy { it.path }, exported.sortedBy { it.path })
        assertEquals(setOf("households", "persons", "regimens", "familyItems", "trips", "packingItems"), exported.map { collectionOf(it.path) }.toSet())

        assertEquals(1, raw.pendingWaits, "enhetens egna skrivningar väntas in före läsningen")
        val file = Json.parseToJsonElement(text).jsonObject
        assertEquals("2", file.getValue("schemaVersion").toString())
        assertEquals("2026-09-21T14:13:20.000Z", file.getValue("exportedAt").jsonPrimitive.content)
    }

    @Test
    fun `bara det aktiva hushållet exporteras`() = runTest {
        seed(fixture + ExportFormat.Document("households/annat/persons/x", mapOf("name" to "Någon annan")))
        val exported = ExportFormat.decode(exporter.export().getOrThrow())
        assertEquals(emptyList(), exported.filter { it.path.startsWith("households/annat") })
    }

    @Test
    fun `utan hushåll eller med ett hushåll som inte finns blir det fel, ingen tom fil`() = runTest {
        scope.householdId.value = null
        assertEquals(DataError.NoHousehold, exporter.export().dataError())
        scope.householdId.value = "finns-inte"
        assertEquals(DataError.NotFound, exporter.export().dataError())
    }

    @Test
    fun `det appen själv skriver kommer med`() = runTest {
        seed(fixture.take(1))
        FakeCollectionFactory(store, scope, clock).persons().upsert(Person("lova", "Lova", "🐰"))
        val lova = ExportFormat.decode(exporter.export().getOrThrow()).single { it.path.endsWith("/persons/lova") }
        assertEquals("Lova", lova.data["name"])
    }

    private fun collectionOf(path: String) = path.split('/').let { it[it.size - 2] }
}
