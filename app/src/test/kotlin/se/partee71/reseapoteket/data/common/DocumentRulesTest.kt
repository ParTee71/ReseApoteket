package se.partee71.reseapoteket.data.common

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant
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
    fun `dokument läses via migratorn, och nyare format läses som de är`() {
        val doc = mapOf("name" to "Anna")
        assertEquals(Person("p", "Anna"), readDocument(PersonCodec, "persons", Schema.CURRENT_VERSION, "p", doc))
        assertEquals(Person("p", "Anna"), readDocument(PersonCodec, "persons", Schema.CURRENT_VERSION + 1, "p", doc))
        assertEquals(Person("p", "Anna"), readDocument(PersonCodec, "persons", null, "p", doc))
    }

    @Test
    fun `skrivskydd bara när hushållets data är nyare än appen`() {
        assertNull(TestHouseholdScope(schemaVersion = Schema.CURRENT_VERSION).writeBlocker())
        assertNull(TestHouseholdScope(schemaVersion = null).writeBlocker())
        assertEquals(DataError.UpdateRequired, TestHouseholdScope(schemaVersion = Schema.CURRENT_VERSION + 1).writeBlocker())
    }

    @Test
    fun `suspendRunCatching mappar fel och släpper igenom DataError`() = runTest {
        assertEquals(DataError.Offline, suspendRunCatching({ DataError.Offline }) { error("x") }.dataError())
        assertEquals(DataError.NoHousehold, suspendRunCatching({ DataError.Unknown }) { throw DataError.NoHousehold }.dataError())
        assertEquals(1, suspendRunCatching({ DataError.Unknown }) { 1 }.getOrThrow())
        assertNull(Result.success(1).dataError())
    }
}
