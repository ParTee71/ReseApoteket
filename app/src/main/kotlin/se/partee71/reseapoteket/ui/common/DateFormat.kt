package se.partee71.reseapoteket.ui.common

import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate

/** Det enda sättet att visa och konvertera datum i UI. */
object DateFormat {
    private val swedish = Locale.forLanguageTag("sv-SE")
    private val display = DateTimeFormatter.ofPattern("EEE d MMM yyyy", swedish)
    private val short = DateTimeFormatter.ofPattern("d MMM", swedish)
    private const val DAY_MILLIS = 86_400_000L

    /** "lör 19 dec 2026" – utan punkterna som svensk CLDR sätter efter förkortningar. */
    fun display(date: LocalDate): String = display.format(date.toJavaLocalDate()).replace(".", "")

    /** "2 okt" – i förvalda namn ("Resa 2 okt"). */
    fun short(date: LocalDate): String = short.format(date.toJavaLocalDate()).replace(".", "")

    /** Midnatt UTC, som Material-datumväljaren räknar. */
    fun toEpochMillis(date: LocalDate): Long = date.toEpochDays().toLong() * DAY_MILLIS

    fun fromEpochMillis(millis: Long): LocalDate = LocalDate.fromEpochDays(Math.floorDiv(millis, DAY_MILLIS).toInt())
}
