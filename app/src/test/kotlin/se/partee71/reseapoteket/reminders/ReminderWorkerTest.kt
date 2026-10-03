package se.partee71.reseapoteket.reminders

import androidx.work.Data
import androidx.work.workDataOf
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test
import se.partee71.reseapoteket.core.engine.ReminderKind

/** Vad bakgrundsjobbet gör (PÅM-6, PÅM-7): genomgång och nästa tid, eller "Senare". */
class ReminderWorkerTest {

    private val next = Instant.fromEpochSeconds(1_790_100_000)
    private val retry = Instant.fromEpochSeconds(1_790_001_800)
    private val scheduled = mutableListOf<Instant>()
    private val shownAgain = mutableListOf<Pair<String, ReminderKind>>()

    private suspend fun perform(input: Data, run: suspend () -> Instant) =
        ReminderWorker.perform(input, run, { trip, kind -> shownAgain += trip to kind }, { scheduled += it }, { retry })

    @Test
    fun `en genomgång schemalägger nästa`() = runTest {
        perform(Data.EMPTY) { next }
        assertEquals(listOf(next), scheduled)
        assertEquals(emptyList(), shownAgain)
    }

    @Test
    fun `en genomgång som fallerar försöker igen i stället för att stanna`() = runTest {
        perform(Data.EMPTY) { error("DataStore går inte att läsa") }
        assertEquals(listOf(retry), scheduled)
    }

    @Test
    fun `Senare visar sin påminnelse igen och schemalägger inget`() = runTest {
        perform(workDataOf(ReminderWorker.TRIP_ID to "gc", ReminderWorker.KIND to "DAY_BEFORE")) { error("ska inte köras") }
        assertEquals(listOf("gc" to ReminderKind.DAY_BEFORE), shownAgain)
        assertEquals(emptyList(), scheduled)
    }

    @Test
    fun `en okänd typ från en äldre app ger en vanlig genomgång`() = runTest {
        perform(workDataOf(ReminderWorker.TRIP_ID to "gc", ReminderWorker.KIND to "PÅ_STATIONEN")) { next }
        assertEquals(listOf(next), scheduled)
    }
}
