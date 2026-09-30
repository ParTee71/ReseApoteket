package se.partee71.reseapoteket.data.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.data.common.HouseholdVersionSource

/** Läser `schemaVersion` direkt ur hushållsdokumentet (utan codec – används av sessionen). */
class FirestoreHouseholdVersions @Inject constructor(private val db: FirebaseFirestore) : HouseholdVersionSource {
    override fun schemaVersion(householdId: String): Flow<Int?> =
        db.document(Paths.household(householdId)).snapshots()
            .map { doc -> if (doc.exists()) (doc.get("schemaVersion") as? Number)?.toInt() ?: Schema.FIRST_VERSION else null }
            .catch { emit(null) }
}
