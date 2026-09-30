package se.partee71.reseapoteket.domain

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import se.partee71.reseapoteket.core.model.Household
import se.partee71.reseapoteket.core.model.HouseholdSettings
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.dataError
import se.partee71.reseapoteket.testing.HouseholdFixture

class EnsureHouseholdUseCaseTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `utan hushåll skapas Mitt hushåll med användaren som enda medlem och väljs`() = runTest(UnconfinedTestDispatcher()) {
        val fixture = HouseholdFixture(backgroundScope, folder.root)

        val status = fixture.ensureHousehold("uid-anna").getOrThrow()

        val created = fixture.households().single()
        assertEquals(HouseholdStatus.Ready(created.id), status)
        assertEquals("Mitt hushåll", created.name)
        assertEquals(listOf("uid-anna"), created.memberUids)
        assertEquals(HouseholdSettings.DEFAULT_MARGIN_DAYS, created.marginDays)
        assertEquals(Schema.CURRENT_VERSION, created.schemaVersion)
        assertEquals(created.id, fixture.session.householdId.value)
    }

    @Test
    fun `befintligt hushåll väljs och inget nytt skapas – det äldsta om det finns flera`() = runTest(UnconfinedTestDispatcher()) {
        val fixture = HouseholdFixture(backgroundScope, folder.root)
        fixture.storeHousehold(Household("nyare", "Sommarstugan", listOf("uid-anna"), createdAt = Instant.fromEpochSeconds(200)))
        fixture.storeHousehold(Household("aldre", "Familjen", listOf("uid-erik", "uid-anna"), createdAt = Instant.fromEpochSeconds(100)))
        fixture.storeHousehold(Household("annans", "Grannen", listOf("uid-lova"), createdAt = Instant.fromEpochSeconds(1)))

        val status = fixture.ensureHousehold("uid-anna").getOrThrow()

        assertEquals(HouseholdStatus.Ready("aldre"), status)
        assertEquals("aldre", fixture.session.householdId.value)
        assertEquals(3, fixture.households().size)
    }

    @Test
    fun `hushåll utan createdAt räknas inte som äldst`() = runTest(UnconfinedTestDispatcher()) {
        val fixture = HouseholdFixture(backgroundScope, folder.root)
        fixture.storeHousehold(Household("a-okand", "Utan datum", listOf("uid-anna"), createdAt = null))
        fixture.storeHousehold(Household("b-daterat", "Familjen", listOf("uid-anna"), createdAt = Instant.fromEpochSeconds(100)))

        assertEquals(HouseholdStatus.Ready("b-daterat"), fixture.ensureHousehold("uid-anna").getOrThrow())
    }

    @Test
    fun `nytt hushåll som servern inte tar emot väljs inte`() = runTest(UnconfinedTestDispatcher()) {
        val fixture = HouseholdFixture(backgroundScope, folder.root)
        fixture.directory.createFailure = DataError.PermissionDenied

        assertEquals(DataError.PermissionDenied, fixture.ensureHousehold("uid-anna").dataError())
        assertEquals(null, fixture.session.householdId.value)
    }

    @Test
    fun `hushåll med nyare schemaversion väljs men kräver uppdatering`() = runTest(UnconfinedTestDispatcher()) {
        val fixture = HouseholdFixture(backgroundScope, folder.root)
        fixture.storeHousehold(Household("h1", "Familjen", listOf("uid-anna"), schemaVersion = Schema.CURRENT_VERSION + 1))

        val status = fixture.ensureHousehold("uid-anna").getOrThrow()

        assertIs<HouseholdStatus.RequiresUpdate>(status)
        assertEquals("h1", fixture.session.householdId.value)
    }

    @Test
    fun `utan nät skapas inget hushåll och felet kommer tillbaka`() = runTest(UnconfinedTestDispatcher()) {
        val fixture = HouseholdFixture(backgroundScope, folder.root)
        fixture.directory.failure = DataError.Offline

        val result = fixture.ensureHousehold("uid-anna")

        assertEquals(DataError.Offline, result.dataError())
        assertEquals(emptyList(), fixture.households())
        assertEquals(null, fixture.session.householdId.value)
    }
}
