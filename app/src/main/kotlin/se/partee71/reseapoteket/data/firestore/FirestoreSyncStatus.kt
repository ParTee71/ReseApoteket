package se.partee71.reseapoteket.data.firestore

import com.google.android.gms.tasks.Task
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.SyncStatus
import se.partee71.reseapoteket.di.ApplicationScope

/**
 * Räknar skrivningar som ännu inte nått servern. En Firestore-skrivning ligger i cachen direkt
 * men dess `Task` blir klar först när servern svarat – offline kan det dröja länge.
 */
@Singleton
class FirestoreSyncStatus @Inject constructor(@ApplicationScope scope: CoroutineScope) : SyncStatus {
    private val pending = MutableStateFlow(0)
    private val errors = MutableSharedFlow<DataError>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val syncing: StateFlow<Boolean> = pending.map { it > 0 }.stateIn(scope, SharingStarted.Eagerly, false)
    override val writeErrors: SharedFlow<DataError> = errors.asSharedFlow()

    /** Följer [task] tills servern svarat; ett fel rapporteras via [writeErrors]. */
    fun track(task: Task<*>) {
        pending.update { it + 1 }
        task.addOnCompleteListener {
            pending.update { count -> count - 1 }
            it.exception?.let { error -> errors.tryEmit(firestoreError(error)) }
        }
    }
}
