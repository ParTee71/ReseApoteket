package se.partee71.reseapoteket.data.common

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Appens enda synkläge: underlag för "synkas…"-indikatorn (NFR-6) och sena skrivfel. */
interface SyncStatus {
    /** Sant så länge någon skrivning väntar på att nå servern. */
    val syncing: StateFlow<Boolean>

    /** Skrivningar som lades i cachen men sedan nekades av servern. */
    val writeErrors: SharedFlow<DataError>
}
