package se.partee71.reseapoteket.data.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import se.partee71.reseapoteket.core.model.Household
import se.partee71.reseapoteket.core.schema.HouseholdCodec
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.prepareForWrite
import se.partee71.reseapoteket.data.common.readDocument
import se.partee71.reseapoteket.data.common.suspendRunCatching
import se.partee71.reseapoteket.data.household.HouseholdDirectory

/** `households where memberUids array-contains uid` – rules tillåter frågan bara med det villkoret. */
class FirestoreHouseholdDirectory @Inject constructor(
    private val db: FirebaseFirestore,
    private val clock: Clock,
) : HouseholdDirectory {

    override suspend fun householdsOf(uid: String): Result<List<Household>> = suspendRunCatching(::firestoreError) {
        db.collection(Paths.HOUSEHOLDS).whereArrayContains(MEMBER_UIDS, uid).get(Source.SERVER).await()
            .documents.mapNotNull { doc ->
                val raw = fromFirestore(doc.data.orEmpty())
                // Varje hushåll läses i sitt eget format – det finns inget aktivt hushåll än. Ett
                // hushåll som inte går att läsa hoppas över i stället för att stänga ute användaren.
                runCatching { readDocument(HouseholdCodec, Paths.HOUSEHOLDS, (raw["schemaVersion"] as? Number)?.toInt(), doc.id, raw) }
                    .getOrNull()
            }
    }

    override suspend fun create(household: Household): Result<Unit> = suspendRunCatching(::firestoreError) {
        val data = toFirestore(prepareForWrite(HouseholdCodec.encode(household), clock.now()))
        // Tasken blir klar när servern svarat; utan svar i tid räknas det som offline. Skrivningen
        // ligger kvar i cachen och kan nå servern senare – nästa försök skriver samma hushåll
        // (DefaultHouseholdRepository återanvänder det), så det blir aldrig två. Merge som alla
        // skrivningar (BCK-3).
        withTimeoutOrNull(CREATE_TIMEOUT) { db.document(Paths.household(household.id)).set(data, SetOptions.merge()).await() }
            ?: throw DataError.Offline
    }

    private companion object {
        const val MEMBER_UIDS = "memberUids"
        val CREATE_TIMEOUT = 15.seconds
    }
}
