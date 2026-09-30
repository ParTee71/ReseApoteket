package se.partee71.reseapoteket.data.common

import kotlinx.coroutines.flow.Flow
import se.partee71.reseapoteket.core.model.Identified

/**
 * En samling dokument av en modell – samma kontrakt för riktig Firestore
 * (`FirestoreCollection`) och för tester (`FakeCollection`), bevisat av `CollectionContract`.
 *
 * - Läsning är flöden från den lokala cachen och följer det aktiva hushållet; utan hushåll
 *   emitteras inget. Listor sorteras på `sortOrder` när modellen är `Sortable`.
 * - Skrivning läggs i cachen direkt och synkas när nätet finns (offline först); `Result`
 *   rapporterar bara fel som syns direkt. Fel från synken kommer via [SyncStatus.lastWriteError].
 * - `upsert` slår ihop med det lagrade dokumentet: okända fält bevaras, `null` tömmer (BCK-3).
 */
interface EntityCollection<T : Identified> {
    fun observe(): Flow<List<T>>

    fun observe(id: String): Flow<T?>

    suspend fun get(id: String): Result<T?>

    suspend fun getAll(): Result<List<T>>

    suspend fun upsert(item: T): Result<Unit>

    suspend fun delete(id: String): Result<Unit>

    /** Ändrar bara `archived`; ett dokument som inte finns skapas inte. */
    suspend fun setArchived(id: String, archived: Boolean): Result<Unit>

    /** Flera skrivningar i ett svep – atomärt inom varje bit om 500 (Firestores gräns). */
    suspend fun batch(upserts: List<T>, deletes: List<String> = emptyList()): Result<Unit>

    /** Klientgenererat, slumpat ID – fungerar offline. */
    fun newId(): String
}
