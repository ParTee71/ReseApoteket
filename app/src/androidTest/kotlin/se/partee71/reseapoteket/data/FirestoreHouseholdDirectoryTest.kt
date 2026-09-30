package se.partee71.reseapoteket.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.reseapoteket.core.model.Household
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.dataError
import se.partee71.reseapoteket.data.firestore.FirestoreHouseholdDirectory

/** Inloggningens fråga mot riktig Firestore med rules aktiva (HUS-1). */
@RunWith(AndroidJUnit4::class)
class FirestoreHouseholdDirectoryTest {

    private val directory = FirestoreHouseholdDirectory(FirebaseEmulator.db, FixedClock())

    private fun test(block: suspend () -> Unit) = runBlocking { withTimeout(20_000) { block() } }

    @Test
    fun egna_hushall_hittas_med_sin_egen_schemaversion() = test {
        val current = FirebaseEmulator.newHousehold()
        val newer = FirebaseEmulator.newHousehold(version = Schema.CURRENT_VERSION + 1)
        val uid = FirebaseEmulator.auth.currentUser!!.uid

        val found = directory.householdsOf(uid).getOrThrow().associateBy { it.id }

        assertEquals(Schema.CURRENT_VERSION, found.getValue(current).schemaVersion)
        assertEquals(Schema.CURRENT_VERSION + 1, found.getValue(newer).schemaVersion)
        assertTrue(found.values.all { uid in it.memberUids })
    }

    @Test
    fun nytt_hushall_finns_pa_servern_nar_create_atervant() = test {
        FirebaseEmulator.newHousehold()
        val uid = FirebaseEmulator.auth.currentUser!!.uid
        val household = Household("test-" + UUID.randomUUID(), "Mitt hushåll", listOf(uid), createdAt = FixedClock().instant)

        directory.create(household).getOrThrow()

        val stored = directory.householdsOf(uid).getOrThrow().single { it.id == household.id }
        assertEquals(household, stored)
    }

    @Test
    fun annan_anvandares_hushall_nekas_av_rules() = test {
        FirebaseEmulator.newHousehold()
        assertEquals(DataError.PermissionDenied, directory.householdsOf("nagon-annan").dataError())
    }

    @Test
    fun utan_nat_ger_fragan_offline_i_stallet_for_en_tom_lista() = test {
        FirebaseEmulator.newHousehold()
        val uid = FirebaseEmulator.auth.currentUser!!.uid
        FirebaseEmulator.db.disableNetwork().await()
        try {
            assertEquals(DataError.Offline, directory.householdsOf(uid).dataError())
        } finally {
            FirebaseEmulator.db.enableNetwork().await()
        }
    }
}
