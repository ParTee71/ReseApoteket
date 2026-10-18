package se.partee71.reseapoteket.core.schema

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.DayOfWeek
import org.junit.Test
import se.partee71.reseapoteket.core.model.DoseSchedule
import se.partee71.reseapoteket.core.model.QuantityRule

class VariantCodecsTest {

    private val schedules = listOf(
        DoseSchedule.Daily(dosesPerDay = 3, unitsPerDose = 2) to DoseSchedule.Daily(),
        DoseSchedule.Weekdays(setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY), unitsPerDose = 2) to DoseSchedule.Weekdays(),
        DoseSchedule.IntervalDays(intervalDays = 3, unitsPerDose = 4) to DoseSchedule.IntervalDays(),
        DoseSchedule.AsNeeded(quantity = 10) to DoseSchedule.AsNeeded()
    )
    private val rules = listOf(
        QuantityRule.Fixed(2) to QuantityRule.Fixed(),
        QuantityRule.PerTraveller(3) to QuantityRule.PerTraveller(),
        QuantityRule.PerDays(quantity = 2, perDays = 5) to QuantityRule.PerDays()
    )

    @Test
    fun `doseringar - alla varianter`() = assertVariantCodecContract(DoseScheduleCodec, schedules)

    @Test
    fun `mängdregler - alla varianter`() = assertVariantCodecContract(QuantityRuleCodec, rules)

    @Test
    fun `byte av doseringstyp nollar den gamla typens fält`() {
        val encoded = asDoc(DoseScheduleCodec.encode(DoseSchedule.AsNeeded(5)))
        assertNull(encoded["dosesPerDay"])
        assertNull(encoded["weekdays"])
        assertEquals(5, encoded["asNeededQuantity"])
    }

    @Test
    fun `veckodagar lagras sorterade som ISO-nummer`() {
        val encoded = asDoc(DoseScheduleCodec.encode(DoseSchedule.Weekdays(setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY))))
        assertEquals(listOf(1, 7), encoded["weekdays"])
    }

    @Test
    fun `okänd typ från en nyare app blir Unknown och skrivs tillbaka oförändrad`() {
        val newer = mapOf("type" to "EVERY_FULL_MOON", "phase" to 3L)
        assertEquals(newer, DoseScheduleCodec.encode(DoseScheduleCodec.decode(newer)))
        assertEquals(newer, QuantityRuleCodec.encode(QuantityRuleCodec.decode(newer)))
        assertEquals(DoseSchedule.Unknown(newer), DoseScheduleCodec.decode(newer))
    }

    @Test
    fun `ett värde som inte ens är en map bevaras också`() {
        val regimen = RegimenCodec.decode("r1", mapOf("name" to "X", "schedule" to "MÅNFAS-3"))
        assertEquals(DoseSchedule.Unknown("MÅNFAS-3"), regimen.schedule)
        assertEquals("MÅNFAS-3", RegimenCodec.encode(regimen)["schedule"])
        val item = FamilyItemCodec.decode("f1", mapOf("rule" to listOf(1, 2)))
        assertEquals(listOf(1, 2), FamilyItemCodec.encode(item)["rule"])
    }

    @Test
    fun `ett recept med okänd dosering överlever en rundtur genom hela codecen`() {
        val newer = mapOf("name" to "Nytt", "schedule" to mapOf("type" to "EVERY_FULL_MOON", "phase" to 3L))
        assertEquals(newer["schedule"], RegimenCodec.encode(RegimenCodec.decode("r1", newer))["schedule"])
    }

    @Test
    fun `saknad dosering eller regel blir Unknown, aldrig en gissad mängd`() {
        assertEquals(DoseSchedule.Unknown(), emptyMap<String, Any?>().nested("schedule", DoseScheduleCodec))
        assertEquals(QuantityRule.Unknown(), emptyMap<String, Any?>().nested("rule", QuantityRuleCodec))
    }
}
