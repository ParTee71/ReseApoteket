package se.partee71.reseapoteket.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import se.partee71.reseapoteket.core.model.PackingItem
import se.partee71.reseapoteket.core.model.Trip
import se.partee71.reseapoteket.core.model.TripStatus

/** Påminnelserna inför en resa (PÅM-1–6). */
class RemindersTest {

    private val zone = TimeZone.of("Europe/Stockholm")
    private val settings = ReminderSettings(personIds = setOf("anna", "lova"))
    private val granCanaria = Trip("gc", "Gran Canaria", startDate = LocalDate(2026, 10, 3), days = 7, travellerIds = listOf("anna", "erik"))

    private fun items(packed: Int, total: Int) = (0 until total).map { PackingItem("r$it", checked = it < packed) }

    private fun plan(vararg trips: Trip, packing: Map<String, List<PackingItem>> = emptyMap(), with: ReminderSettings = settings) =
        ReminderPlanner.plan(trips.toList(), packing, with)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0) = LocalDateTime(year, month, day, hour, minute)

    @Test
    fun `en lista som inte är packad ger dags att packa, dagen före och resedagen`() {
        val planned = plan(granCanaria, packing = mapOf("gc" to items(9, 16)))
        assertEquals(
            listOf(
                ReminderKind.TIME_TO_PACK to at(2026, 9, 30, 18),
                ReminderKind.DAY_BEFORE to at(2026, 10, 2, 18),
                ReminderKind.TRAVEL_DAY to at(2026, 10, 3, 7),
            ),
            planned.map { it.kind to it.at },
        )
        assertEquals(PackingProgress(9, 16), planned.first().progress)
    }

    @Test
    fun `utan packlista kommer bara ingen packlista än, sju dagar före`() {
        val planned = plan(granCanaria)
        assertEquals(listOf(ReminderKind.NO_LIST to at(2026, 9, 26, 18)), planned.map { it.kind to it.at })
    }

    @Test
    fun `en packad lista ger inga påminnelser, inte heller dolda rader`() {
        val hidden = PackingItem("dold", hidden = true)
        assertEquals(emptyList(), plan(granCanaria, packing = mapOf("gc" to items(3, 3) + hidden)))
    }

    @Test
    fun `bara resor som planeras, har startdatum och där en vald person reser`() {
        val packing = mapOf("gc" to items(0, 2))
        assertEquals(emptyList(), plan(granCanaria.copy(status = TripStatus.PACKED), packing = packing))
        assertEquals(emptyList(), plan(granCanaria.copy(status = TripStatus.DONE), packing = packing))
        assertEquals(emptyList(), plan(granCanaria.copy(archived = true), packing = packing))
        assertEquals(emptyList(), plan(granCanaria.copy(startDate = null), packing = packing))
        assertEquals(emptyList(), plan(granCanaria.copy(travellerIds = listOf("erik")), packing = packing))
        assertEquals(emptyList(), plan(granCanaria, packing = packing, with = ReminderSettings()))
    }

    @Test
    fun `avstängda påminnelser och egna tider och dagar följs`() {
        val custom = settings
            .with(ReminderKind.TIME_TO_PACK, ReminderSetting(true, LocalTime(20, 30), 5))
            .with(ReminderKind.DAY_BEFORE, ReminderSetting(false, LocalTime(18, 0), 1))
            .with(ReminderKind.TRAVEL_DAY, ReminderSetting(true, LocalTime(6, 15), 0))
        val planned = plan(granCanaria, packing = mapOf("gc" to items(0, 1)), with = custom)
        assertEquals(listOf(at(2026, 9, 28, 20, 30), at(2026, 10, 3, 6, 15)), planned.map { it.at })
    }

    @Test
    fun `dagar före hålls inom intervallet och går över månads- och årsskifte`() {
        val newYear = granCanaria.copy(startDate = LocalDate(2027, 1, 1))
        val custom = settings.with(ReminderKind.NO_LIST, ReminderSetting(true, LocalTime(18, 0), 400))
        assertEquals(listOf(at(2026, 12, 2, 18)), plan(newYear, with = custom).map { it.at })
        val dayBefore = plan(newYear, packing = mapOf("gc" to items(0, 1))).first { it.kind == ReminderKind.DAY_BEFORE }
        assertEquals(at(2026, 12, 31, 18), dayBefore.at)
    }

    @Test
    fun `förvalen är sju och tre dagar före kl 18 och resedagen kl 7`() {
        val defaults = ReminderSettings()
        assertEquals(ReminderSetting(true, LocalTime(18, 0), 7), defaults[ReminderKind.NO_LIST])
        assertEquals(ReminderSetting(true, LocalTime(18, 0), 3), defaults[ReminderKind.TIME_TO_PACK])
        assertEquals(LocalTime(18, 0), defaults[ReminderKind.DAY_BEFORE].time)
        assertEquals(LocalTime(7, 0), defaults[ReminderKind.TRAVEL_DAY].time)
    }

    @Test
    fun `förfallna påminnelser är de mellan förra körningen och nu, inte för gamla`() {
        val planned = plan(granCanaria, packing = mapOf("gc" to items(0, 1)))
        val dayBefore = at(2026, 10, 2, 18).toInstant(zone)
        val due = { since: LocalDateTime, now: LocalDateTime -> ReminderPlanner.due(planned, since.toInstant(zone), now.toInstant(zone), zone, 2.hours).map { it.kind } }
        assertEquals(listOf(ReminderKind.DAY_BEFORE), due(at(2026, 10, 2, 12), at(2026, 10, 2, 18, 5)))
        assertEquals(emptyList(), due(at(2026, 10, 2, 18), at(2026, 10, 2, 19)))
        assertEquals(emptyList(), due(at(2026, 10, 1, 12), at(2026, 10, 2, 21)))
        assertEquals(dayBefore, ReminderPlanner.next(planned, dayBefore - 1.minutes, zone))
    }

    @Test
    fun `nästa påminnelse räknas i telefonens tidszon, även vid sommartidens slut`() {
        val autumn = granCanaria.copy(startDate = LocalDate(2026, 10, 25))
        val planned = plan(autumn, packing = mapOf("gc" to items(0, 1)))
        val travelDay = planned.single { it.kind == ReminderKind.TRAVEL_DAY }
        // 07:00 svensk vintertid är 06:00 UTC.
        assertEquals(LocalDateTime(2026, 10, 25, 6, 0).toInstant(TimeZone.UTC), travelDay.at.toInstant(zone))
        assertNull(ReminderPlanner.next(planned, travelDay.at.toInstant(zone), zone))
    }
}
