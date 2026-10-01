package se.partee71.reseapoteket.data.common

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.schema.PersonCodec
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.data.TestHouseholdScope

class DocumentRulesTest {

    private val now = Instant.fromEpochSeconds(100)

    @Test
    fun `updatedAt sätts alltid och createdAt utan värde skrivs inte`() {
        val prepared = prepareForWrite(mapOf("name" to "x", "createdAt" to null, "updatedAt" to null), now)
        assertEquals(mapOf("name" to "x", "updatedAt" to now), prepared)
        assertFalse("createdAt" in prepared)
        val kept = prepareForWrite(mapOf("createdAt" to Instant.fromEpochSeconds(1)), now)
        assertEquals(Instant.fromEpochSeconds(1), kept["createdAt"])
    }

    @Test
    fun `äldre format går genom migreringen före decode, nuvarande och nyare läses som de är`() {
        val calls = mutableListOf<Int>()
        val renameNamn: Migrate = { from, collection, doc ->
            calls += from
            assertEquals("persons", collection)
            doc - "namn" + ("name" to doc["namn"])
        }
        val old = mapOf("namn" to "Anna")
        assertEquals(Person("p", "Anna"), readDocument(PersonCodec, "persons", 0, "p", old, renameNamn))
        assertEquals(listOf(0), calls)
        val current = mapOf("name" to "Anna")
        assertEquals(Person("p", "Anna"), readDocument(PersonCodec, "persons", Schema.CURRENT_VERSION, "p", current, renameNamn))
        assertEquals(Person("p", "Anna"), readDocument(PersonCodec, "persons", Schema.CURRENT_VERSION + 1, "p", current, renameNamn))
        assertEquals(Person("p", "Anna"), readDocument(PersonCodec, "persons", null, "p", current, renameNamn))
        assertEquals(listOf(0), calls, "migreringen körs bara för äldre format")
    }

    @Test
    fun `listor sorteras på id och sedan sortOrder`() {
        val sorted = sortForList(listOf(Person("c"), Person("b", sortOrder = 1), Person("a")))
        assertEquals(listOf("a", "c", "b"), sorted.map { it.id })
    }

    @Test
    fun `skrivskydd när hushållets data är nyare än appen`() = runTest {
        assertNull(TestHouseholdScope(version = Schema.CURRENT_VERSION).writeBlocker())
        assertEquals(DataError.UpdateRequired, TestHouseholdScope(version = Schema.CURRENT_VERSION + 1).writeBlocker())
        assertNull(TestHouseholdScope(householdId = null, version = null).writeBlocker())
    }

    @Test
    fun `okänd version spärrar skrivning - väntar först, sedan vägras den`() = runTest {
        val scope = TestHouseholdScope(version = null)
        var result: DataError? = DataError.Unknown
        val job = launch { result = scope.writeBlocker() }
        advanceTimeBy(1_000)
        scope.setVersion(Schema.CURRENT_VERSION)
        job.join()
        assertNull(result, "versionen kom inom väntetiden")
        assertEquals(DataError.Offline, TestHouseholdScope(version = null).writeBlocker(), "versionen kom aldrig")
    }

    @Test
    fun `ett hushåll som inte går att läsa nekar skrivning i stället för att vänta ut den (HUS-5)`() = runTest {
        val scope = TestHouseholdScope(version = null)
        var result: DataError? = null
        val job = launch { result = scope.writeBlocker() }
        advanceTimeBy(1_000)
        scope.unreadable.value = "hushall-1"
        job.join()
        assertEquals(DataError.PermissionDenied, result)
        assertEquals(1_000L, testScheduler.currentTime, "nekandet väntar inte ut tidsgränsen")
    }

    @Test
    fun `en version från förra hushållet gäller inte det nya`() = runTest {
        val scope = TestHouseholdScope(householdId = "gammalt", version = Schema.CURRENT_VERSION)
        scope.householdId.value = "nytt"
        assertNull(scope.currentVersion())
        assertEquals(DataError.Offline, scope.writeBlocker())
    }

    @Test
    fun `suspendRunCatching mappar fel och släpper igenom DataError`() = runTest {
        assertEquals(DataError.Offline, suspendRunCatching({ DataError.Offline }) { error("x") }.dataError())
        assertEquals(DataError.NoHousehold, suspendRunCatching({ DataError.Unknown }) { throw DataError.NoHousehold }.dataError())
        assertEquals(1, suspendRunCatching({ DataError.Unknown }) { 1 }.getOrThrow())
        assertNull(Result.success(1).dataError())
    }
}
