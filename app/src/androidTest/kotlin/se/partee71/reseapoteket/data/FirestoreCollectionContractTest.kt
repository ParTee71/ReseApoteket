package se.partee71.reseapoteket.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.runner.RunWith
import se.partee71.reseapoteket.core.schema.Doc
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.data.firestore.fromFirestore
import se.partee71.reseapoteket.data.firestore.toFirestore

/**
 * `CollectionContract` mot riktig `FirestoreCollection` och Firebase-emulatorn, med rules och
 * anonym inloggning i auth-emulatorn. Körs av instrumented.yml: på begäran och i release.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreCollectionContractTest : CollectionContract() {

    override fun createEnvironment(): Environment = runBlocking {
        val householdId = FirebaseEmulator.newHousehold()
        val scope = TestHouseholdScope(householdId)
        val collections = FirebaseEmulator.collections(scope)
        val db = FirebaseEmulator.db
        object : Environment {
            override val householdId = householdId
            override val collections = collections.factory
            override val now = collections.clock.instant

            override suspend fun writeRaw(path: String, id: String, doc: Doc) {
                db.collection(path).document(id).set(toFirestore(doc)).await()
            }

            override suspend fun readRaw(path: String, id: String): Doc? =
                db.collection(path).document(id).get().await().data?.let(::fromFirestore)

            override fun makeHouseholdNewerThanApp() = scope.setVersion(Schema.CURRENT_VERSION + 1)

            override fun clearHousehold() {
                scope.householdId.value = null
            }
        }
    }
}
