package se.partee71.reseapoteket.data.common

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import org.junit.Test
import se.partee71.reseapoteket.ui.common.toMessage

class AppErrorsTest {

    @Test
    fun `ett DataError blir ett UiError med appens text, och bara det som inte finns erbjuder inte Försök igen`() {
        listOf(DataError.Offline, DataError.PermissionDenied, DataError.UpdateRequired, DataError.Unknown).forEach { error ->
            val mapped = AppErrors.map(error)
            assertEquals(error, mapped.cause)
            assertEquals(error.toMessage(), mapped.message)
            assertTrue(mapped.retryable, "$error ska gå att försöka igen")
        }
        val gone = AppErrors.notFound()
        assertEquals(DataError.NotFound, gone.cause)
        assertEquals(DataError.NotFound.toMessage(), gone.message)
        assertFalse(gone.retryable)
        assertFalse(AppErrors.map(DataError.NotFound).retryable)
    }

    @Test
    fun `ett oväntat fel blir Unknown och dess meddelande följer inte med`() {
        val mapped = AppErrors.map(IllegalStateException("Levaxin 50 µg"))
        assertEquals(DataError.Unknown, mapped.cause)
        assertEquals(DataError.Unknown.toMessage(), mapped.message)
        assertFalse("Levaxin" in mapped.toString())
    }

    @Test
    fun `samma fel två gånger är två olika händelser`() {
        assertNotSame(AppErrors.map(DataError.Offline), AppErrors.map(DataError.Offline))
    }
}
