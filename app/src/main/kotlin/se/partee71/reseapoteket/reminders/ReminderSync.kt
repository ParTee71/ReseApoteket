package se.partee71.reseapoteket.reminders

import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce

/**
 * Medan appen syns följer påminnelserna resorna, listorna och inställningarna: varje ändring
 * flyttar nästa genomgång direkt (PÅM-6). I bakgrunden sköter [ReminderWorker] det själv.
 */
class ReminderSync @Inject constructor(
    private val source: ReminderSource,
    private val runner: ReminderRunner,
    private val scheduler: ReminderScheduler,
    private val clock: Clock
) {
    @OptIn(FlowPreview::class)
    suspend fun follow() {
        source.plan
            .debounce(SETTLE)
            // Utloggad eller oläsbart: bakgrundsjobbet försöker igen själv; följ inte vidare nu.
            .catch { }
            .collect { planned -> scheduler.checkAt(runner.nextCheck(planned, runner.checkedUntil(), clock.now())) }
    }

    private companion object {
        val SETTLE = 2.seconds
    }
}
