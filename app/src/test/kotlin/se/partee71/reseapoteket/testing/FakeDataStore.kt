package se.partee71.reseapoteket.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Inställningar i minnet med styrbara fel: läsningen fallerar [readFailures] gånger innan den
 * lyckas, [writeFailure] kastas vid varje skrivning, och [frozen] gör att läsningen bara
 * visar startvärdet (som när filen inte kan läsas tillbaka).
 */
class FakeDataStore(
    private var readFailures: Int = 0,
    private val initial: Preferences = emptyPreferences(),
    var writeFailure: IOException? = null,
    private val frozen: Boolean = false
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)

    override val data: Flow<Preferences> = flow {
        if (readFailures > 0) {
            readFailures--
            throw IOException("kan inte läsa")
        }
        if (frozen) {
            emit(initial)
            awaitCancellation()
        }
        emitAll(state)
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        writeFailure?.let { throw it }
        return transform(state.value).also { state.value = it }
    }
}
