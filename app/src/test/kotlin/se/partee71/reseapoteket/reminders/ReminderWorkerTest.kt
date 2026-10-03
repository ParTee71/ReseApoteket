package se.partee71.reseapoteket.reminders

import androidx.work.Data
import androidx.work.workDataOf
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Vad bakgrundsjobbet gör (PÅM-6, PÅM-7): genomgång och nästa tid, eller "Senare". */
class ReminderWorkerTest {

    private val next = Instant.fromEpochSeconds(1_790_100_000)
    private val retry = Instant.fromEpochSeconds(1_790_001_800)
    private val scheduled = mutableListOf<Instant>()
    private val shownAgain = mutableListOf<String>()

    private suspend fun perform(input: Data, run: suspend () -> Instant) =
        ReminderWorker.perform(input, run, { key -> shownAgain += key }, { scheduled += it }, { retry })

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
    fun `ett jobb utan nyckel – även ett "Senare" från en äldre app – ger en vanlig genomgång`() = runTest {
        perform(workDataOf("tripId" to "gc", "kind" to "DAY_BEFORE")) { next }
        assertEquals(listOf(next), scheduled)
        assertEquals(emptyList(), shownAgain)
    }

    @Test
    fun `Senare visar sin påminnelse igen och schemalägger inget`() = runTest {
        perform(workDataOf(ReminderWorker.KEY to "gc/DAY_BEFORE/18:00")) { error("ska inte köras") }
        assertEquals(listOf("gc/DAY_BEFORE/18:00"), shownAgain)
        assertEquals(emptyList(), scheduled)
    }

}
