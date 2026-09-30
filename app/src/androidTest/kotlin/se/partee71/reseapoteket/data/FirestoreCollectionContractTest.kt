package se.partee71.reseapoteket.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.runner.RunWith
import se.partee71.reseapoteket.core.schema.Doc
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.data.firestore.FirestoreCollectionFactory
import se.partee71.reseapoteket.data.firestore.FirestoreSyncStatus
import se.partee71.reseapoteket.data.firestore.Paths
import se.partee71.reseapoteket.data.firestore.fromFirestore
import se.partee71.reseapoteket.data.firestore.toFirestore

/**
 * `CollectionContract` mot riktig `FirestoreCollection` och Firebase-emulatorn, med rules och
 * inloggning (anonym, i auth-emulatorn). Körs av instrumented.yml: på begäran och i release.
 * Rör aldrig den riktiga databasen – projektet är `demo-reseapoteket` (BCK-4).
 */
@RunWith(AndroidJUnit4::class)
class FirestoreCollectionContractTest : CollectionContract() {

    override fun createEnvironment(): Environment = runBlocking {
        val uid = Emulator.auth.currentUser?.uid ?: Emulator.auth.signInAnonymously().await().user!!.uid
        val householdId = "kontrakt-" + UUID.randomUUID()
        Emulator.db.document(Paths.household(householdId))
            .set(mapOf("memberUids" to listOf(uid), "schemaVersion" to Schema.CURRENT_VERSION.toLong()))
            .await()
        val scope = TestHouseholdScope(householdId)
        val clock = FixedClock()
        val sync = FirestoreSyncStatus(CoroutineScope(Dispatchers.Default))
        object : Environment {
            override val householdId = householdId
            override val collections = FirestoreCollectionFactory(Emulator.db, scope, sync, clock)
            override val now = clock.instant

            override suspend fun writeRaw(path: String, id: String, doc: Doc) {
                Emulator.db.collection(path).document(id).set(toFirestore(doc)).await()
            }

            override suspend fun readRaw(path: String, id: String): Doc? =
                Emulator.db.collection(path).document(id).get().await().data?.let(::fromFirestore)

            override fun makeHouseholdNewerThanApp() {
                scope.schemaVersion.value = Schema.CURRENT_VERSION + 1
            }
        }
    }

    /** En egen FirebaseApp mot emulatorerna på värddatorn (10.0.2.2 från Android-emulatorn). */
    private object Emulator {
        private val app: FirebaseApp by lazy {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val options = FirebaseOptions.Builder()
                .setProjectId("demo-reseapoteket")
                .setApplicationId("1:1:android:1")
                .setApiKey("demo-nyckel")
                .build()
            FirebaseApp.initializeApp(context, options, "kontrakt")
        }
        val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance(app).apply { useEmulator(HOST, 8080) } }
        val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance(app).apply { useEmulator(HOST, 9099) } }
        private const val HOST = "10.0.2.2"
    }
}
