package se.partee71.reseapoteket.data

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.data.firestore.FirestoreCollectionFactory
import se.partee71.reseapoteket.data.firestore.FirestoreHouseholdVersions
import se.partee71.reseapoteket.data.firestore.FirestoreSyncStatus
import se.partee71.reseapoteket.data.firestore.Paths

/**
 * En egen FirebaseApp mot Firebase-emulatorerna på värddatorn (10.0.2.2 från Android-emulatorn),
 * projekt `demo-reseapoteket`. Rör aldrig den riktiga databasen (BCK-4).
 */
object FirebaseEmulator {
    private const val HOST = "10.0.2.2"

    private val app: FirebaseApp by lazy {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val options = FirebaseOptions.Builder()
            .setProjectId("demo-reseapoteket")
            .setApplicationId("1:1:android:1")
            .setApiKey("demo-nyckel")
            .build()
        FirebaseApp.initializeApp(context, options, "emulator")
    }

    val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(app).apply {
            useEmulator(HOST, 8080)
            check(firestoreSettings.host == "$HOST:8080") { "Testerna får bara köras mot emulatorn" }
        }
    }

    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance(app).apply { useEmulator(HOST, 9099) } }

    /** Ett nytt hushåll där den (anonymt) inloggade testanvändaren är medlem. */
    suspend fun newHousehold(version: Int = Schema.CURRENT_VERSION): String {
        val uid = auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid
        val householdId = "test-" + UUID.randomUUID()
        db.document(Paths.household(householdId))
            .set(mapOf("memberUids" to listOf(uid), "schemaVersion" to version.toLong()))
            .await()
        return householdId
    }

    fun collections(scope: TestHouseholdScope, clock: FixedClock = FixedClock()) = Collections(FirestoreCollectionFactory(db, scope, sync(), clock), clock)

    fun versions() = FirestoreHouseholdVersions(db)

    fun sync() = FirestoreSyncStatus(CoroutineScope(Dispatchers.Default))

    data class Collections(val factory: FirestoreCollectionFactory, val clock: FixedClock)
}
