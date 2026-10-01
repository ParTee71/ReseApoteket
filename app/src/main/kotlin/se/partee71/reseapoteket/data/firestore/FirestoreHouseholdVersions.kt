package se.partee71.reseapoteket.data.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.snapshots
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.HouseholdVersionSource
import se.partee71.reseapoteket.data.common.retryWithBackoff

/**
 * Läser `schemaVersion` direkt ur hushållsdokumentet (utan codec – används av sessionen).
 * Ett dokument som saknas i cachen (utan nät), saknar fältet eller har ett värde under det
 * första formatet får [Schema.FIRST_VERSION] (Schema.versionOf).
 * Nekar servern läsningen (inte medlem, eller dokumentet saknas – rules läser `resource.data`)
 * avslutas flödet med [DataError.PermissionDenied] så att sessionen väljer bort hushållet
 * (HUS-5). Bara `PERMISSION_DENIED` räknas: ett tillfälligt `UNAUTHENTICATED` (token som
 * förnyas) och alla andra fel försöker igen, så att versionen aldrig blir okänd för gott.
 */
class FirestoreHouseholdVersions @Inject constructor(private val db: FirebaseFirestore) : HouseholdVersionSource {
    override fun schemaVersion(householdId: String): Flow<Int> =
        db.document(Paths.household(householdId)).snapshots()
            .map { doc -> Schema.versionOf(doc.get("schemaVersion")) }
            .catch { error ->
                throw if (error is FirebaseFirestoreException && error.code == Code.PERMISSION_DENIED) DataError.PermissionDenied else error
            }
            .retryWithBackoff(first = 2.seconds, max = 60.seconds, retryOn = { it != DataError.PermissionDenied })

    /** `update`, inte `set`: bara versionen ändras, och ett hushåll som inte finns skapas inte. */
    override fun stamp(householdId: String, version: Int) {
        db.document(Paths.household(householdId)).update("schemaVersion", version)
    }
}
