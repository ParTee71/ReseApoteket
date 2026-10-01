package se.partee71.reseapoteket.navigation

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** NAV-1–3: flikar med egna stackar, start på Resor, tillbaka via Resor. */
class AppBackStackTest {

    @Test
    fun `appen startar på Resor med verktygsraden synlig`() {
        val stack = AppBackStack()
        assertEquals(listOf<AppKey>(TripsKey), stack.entries)
        assertTrue(stack.atTopLevel)
    }

    @Test
    fun `en undersida döljer verktygsraden och tillbaka visar den igen`() {
        val stack = AppBackStack()
        stack.push(TripEditKey())
        assertFalse(stack.atTopLevel)
        assertTrue(stack.pop())
        assertTrue(stack.atTopLevel)
    }

    @Test
    fun `varje flik minns sin undersida när man byter flik`() {
        val stack = AppBackStack()
        stack.select(PersonsKey)
        stack.push(PersonDetailKey("anna"))
        stack.select(FamilyItemsKey)
        assertEquals(listOf(TripsKey, FamilyItemsKey), stack.entries)
        stack.select(PersonsKey)
        assertEquals(listOf(TripsKey, PersonsKey, PersonDetailKey("anna")), stack.entries)
    }

    @Test
    fun `tillbaka i roten av en annan flik går till Resor, i Resors rot lämnar appen`() {
        val stack = AppBackStack()
        stack.select(SettingsKey)
        assertTrue(stack.pop())
        assertEquals(TripsKey, stack.currentTab)
        assertFalse(stack.pop())
    }

    @Test
    fun `att välja fliken man står på går till flikens rot`() {
        val stack = AppBackStack()
        stack.select(PersonsKey)
        stack.push(PersonDetailKey("anna"))
        stack.push(RegimenEditKey("anna", se.partee71.reseapoteket.core.model.RegimenKind.MEDICINE))
        stack.select(PersonsKey)
        assertEquals(listOf(TripsKey, PersonsKey), stack.entries)
    }

    @Test
    fun `flikar väljs, de läggs inte på stacken`() {
        assertFailsWith<IllegalArgumentException> { AppBackStack().push(PersonsKey) }
    }

    @Test
    fun `stackarna överlever att sparas och återställas`() {
        val stack = AppBackStack()
        stack.push(PackingListKey("resa-1"))
        stack.select(PersonsKey)
        stack.push(PersonEditKey("anna"))
        val restored = AppBackStack.restore(stack.save())
        assertEquals(stack.entries, restored.entries)
        assertEquals(PersonsKey, restored.currentTab)
        restored.select(TripsKey)
        assertEquals(listOf(TripsKey, PackingListKey("resa-1")), restored.entries)
    }

    @Test
    fun `ett dubbeltryck öppnar skärmen en gång (NAV-2)`() {
        val stack = AppBackStack()
        stack.push(TripEditKey())
        stack.push(TripEditKey())
        assertEquals(listOf(TripsKey, TripEditKey()), stack.entries)
        stack.push(PackingListKey("t1"))
        stack.push(PackingListKey("t2"))
        assertEquals(listOf(TripsKey, TripEditKey(), PackingListKey("t1"), PackingListKey("t2")), stack.entries)
    }

    @Test
    fun `en skärm som stänger sig två gånger stänger inte skärmen under (NAV-2)`() {
        val stack = AppBackStack()
        stack.select(PersonsKey)
        stack.push(PersonDetailKey("p1"))
        stack.push(PersonEditKey("p1"))

        stack.popIfTop(PersonEditKey("p1"))
        stack.popIfTop(PersonEditKey("p1"))

        assertEquals(listOf(TripsKey, PersonsKey, PersonDetailKey("p1")), stack.entries)
    }
}
