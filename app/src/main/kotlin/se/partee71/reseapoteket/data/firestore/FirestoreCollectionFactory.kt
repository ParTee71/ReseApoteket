package se.partee71.reseapoteket.data.firestore

import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import se.partee71.reseapoteket.core.model.Identified
import se.partee71.reseapoteket.core.schema.DocCodec
import se.partee71.reseapoteket.data.common.EntityCollection
import se.partee71.reseapoteket.data.common.HouseholdScope

/** Samlingarna i [CollectionTable] som riktiga [FirestoreCollection]. */
@Singleton
class FirestoreCollectionFactory @Inject constructor(
    private val db: FirebaseFirestore,
    private val scope: HouseholdScope,
    private val sync: FirestoreSyncStatus,
    private val clock: Clock,
) : CollectionTable() {
    override fun <T : Identified> create(
        codec: DocCodec<T>,
        name: String,
        path: (householdId: String?) -> String,
    ): EntityCollection<T> = FirestoreCollection(db, scope, sync, clock, codec, name, path)
}
