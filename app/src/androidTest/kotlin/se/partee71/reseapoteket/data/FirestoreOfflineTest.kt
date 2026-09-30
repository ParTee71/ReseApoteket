package se.partee71.reseapoteket.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.Source
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.data.firestore.FirestoreCollectionFactory
import se.partee71.reseapoteket.data.firestore.FirestoreHouseholdVersions
import se.partee71.reseapoteket.data.firestore.Paths

/** Offline först och hushållets version, mot riktig Firestore i emulatorn (ÖV-5, BCK-5). */
@RunWith(AndroidJUnit4::class)
class FirestoreOfflineTest {

    private fun test(block: suspend () -> Unit) = runBlocking { withTimeout(20_000) { block() } }

    @Test
    fun skrivning_offline_lyckas_direkt_syns_lokalt_och_synkas_nar_natet_kommer_tillbaka() = test {
        val householdId = FirebaseEmulator.newHousehold()
        val sync = FirebaseEmulator.sync()
        val persons = FirestoreCollectionFactory(FirebaseEmulator.db, TestHouseholdScope(householdId), sync, FixedClock()).persons()
        FirebaseEmulator.db.disableNetwork().await()
        try {
            assertTrue(persons.upsert(Person("a", "Anna")).isSuccess)
            assertEquals("Anna", persons.observe("a").first { it != null }?.name)
            assertTrue(sync.syncing.first { it })
        } finally {
            FirebaseEmulator.db.enableNetwork().await()
        }
        sync.syncing.first { !it }
        val server = FirebaseEmulator.db.collection(Paths.persons(householdId)).document("a")
            .get(Source.SERVER).await()
        assertEquals("Anna", server.getString("name"))
    }

    @Test
    fun hushallets_version_lases_ur_dokumentet() = test {
        val versions = FirestoreHouseholdVersions(FirebaseEmulator.db)
        val current = FirebaseEmulator.newHousehold()
        val newer = FirebaseEmulator.newHousehold(version = Schema.CURRENT_VERSION + 1)
        assertEquals(Schema.CURRENT_VERSION, versions.schemaVersion(current).first())
        assertEquals(Schema.CURRENT_VERSION + 1, versions.schemaVersion(newer).first())
    }
}
