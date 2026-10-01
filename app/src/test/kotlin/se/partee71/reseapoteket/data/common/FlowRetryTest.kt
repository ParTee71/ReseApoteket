package se.partee71.reseapoteket.data.common

import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Omförsök för flöden som appen följer hela tiden (hushållets version, inställningarna). */
class FlowRetryTest {

    @Test
    fun `pausen dubblas för varje försök upp till taket`() {
        val pauses = (0L..6L).map { backoff(2.seconds, 60.seconds, it) }
        assertEquals(listOf(2, 4, 8, 16, 32, 60, 60).map { it.seconds }, pauses)
        assertEquals(60.seconds, backoff(2.seconds, 60.seconds, Long.MAX_VALUE), "inget överslag")
    }

    @Test
    fun `godkända fel försöks igen efter pausen och ersättningsvärdet sänds först`() = runTest {
        var failures = 2
        val source = flow {
            if (failures-- > 0) throw IOException()
            emit("värde")
        }

        val values = source.retryWithBackoff(1.seconds, 30.seconds, retryOn = { it is IOException }, beforeRetry = { emit("standard") }).toList()

        assertEquals(listOf("standard", "standard", "värde"), values)
        assertEquals(3_000L, testScheduler.currentTime, "1 s + 2 s")
    }

    @Test
    fun `andra fel släpps igenom direkt`() = runTest {
        val source = flow<String> { throw DataError.PermissionDenied }
        assertFailsWith<DataError.PermissionDenied> {
            source.retryWithBackoff(1.seconds, 30.seconds, retryOn = { it is IOException }).toList()
        }
        assertEquals(0L, testScheduler.currentTime)
    }
}
