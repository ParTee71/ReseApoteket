package se.partee71.reseapoteket.data.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import javax.inject.Inject
import kotlinx.coroutines.tasks.await
import se.partee71.reseapoteket.core.model.Household
import se.partee71.reseapoteket.core.schema.HouseholdCodec
import se.partee71.reseapoteket.data.common.readDocument
import se.partee71.reseapoteket.data.common.suspendRunCatching
import se.partee71.reseapoteket.data.household.HouseholdDirectory

/** `households where memberUids array-contains uid` – rules tillåter frågan bara med det villkoret. */
class FirestoreHouseholdDirectory @Inject constructor(private val db: FirebaseFirestore) : HouseholdDirectory {
    override suspend fun householdsOf(uid: String): Result<List<Household>> = suspendRunCatching(::firestoreError) {
        db.collection(Paths.HOUSEHOLDS).whereArrayContains(MEMBER_UIDS, uid).get(Source.SERVER).await()
            .documents.map { doc ->
                val raw = fromFirestore(doc.data.orEmpty())
                // Varje hushåll läses i sitt eget format – det finns inget aktivt hushåll än.
                readDocument(HouseholdCodec, Paths.HOUSEHOLDS, (raw["schemaVersion"] as? Number)?.toInt(), doc.id, raw)
            }
    }

    private companion object {
        const val MEMBER_UIDS = "memberUids"
    }
}
