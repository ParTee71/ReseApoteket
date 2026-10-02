package se.partee71.reseapoteket.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.schema.ExportFormat
import se.partee71.reseapoteket.data.firestore.FirestoreRawDocuments
import se.partee71.reseapoteket.data.firestore.HouseholdExporter
import se.partee71.reseapoteket.data.firestore.Paths

/**
 * "Exportera som JSON" mot riktig Firestore med rules aktiva (SET-5): enhetens egna skrivningar
 * väntas in, och dokumenten läses från servern.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreHouseholdExporterTest {

    private fun test(block: suspend () -> Unit) = runBlocking { withTimeout(30_000) { block() } }

    @Test
    fun exporten_innehaller_det_som_nyss_skrivits_pa_enheten() = test {
        val householdId = FirebaseEmulator.newHousehold()
        val scope = TestHouseholdScope(householdId)
        val collections = FirebaseEmulator.collections(scope)
        collections.factory.persons().upsert(Person("anna", "Anna", "🦊")).getOrThrow()

        val text = HouseholdExporter(FirestoreRawDocuments(FirebaseEmulator.db), scope, collections.clock).export().getOrThrow()

        val paths = ExportFormat.decode(text).map { it.path }
        assertEquals(listOf(Paths.household(householdId), "${Paths.persons(householdId)}/anna"), paths)
    }
}
