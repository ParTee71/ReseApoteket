package se.partee71.reseapoteket.ui.common

import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import org.junit.Test

class DateFormatTest {

    @Test
    fun `datum visas på svenska utan punkter`() {
        assertEquals("lör 19 dec 2026", DateFormat.display(LocalDate(2026, 12, 19)))
    }

    @Test
    fun `millisekunder från datumväljaren ger samma datum tillbaka`() {
        val date = LocalDate(2026, 2, 28)
        assertEquals(date, DateFormat.fromEpochMillis(DateFormat.toEpochMillis(date)))
        assertEquals(LocalDate(1969, 12, 31), DateFormat.fromEpochMillis(-1))
    }
}
