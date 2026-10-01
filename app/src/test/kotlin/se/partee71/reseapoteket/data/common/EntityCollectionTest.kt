package se.partee71.reseapoteket.data.common

import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.data.FakeCollectionFactory
import se.partee71.reseapoteket.data.TestHouseholdScope

/** `nextSortOrder`/`upsertPlaced`: något nytt hamnar sist, och sparandet väntar aldrig på nätet. */
class EntityCollectionTest {

    @Test
    fun `något nytt hamnar efter det sista i listan`() = runTest {
        val persons = FakeCollectionFactory().persons()
        persons.upsert(Person("a", "Anna", sortOrder = 4)).getOrThrow()
        persons.upsertPlaced(Person("b", "Bo"), isNew = true) { copy(sortOrder = it) }.getOrThrow()
        assertEquals(5, persons.get("b").getOrThrow()?.sortOrder)
        persons.upsertPlaced(Person("b", "Bo", sortOrder = 1), isNew = false) { copy(sortOrder = it) }.getOrThrow()
        assertEquals(1, persons.get("b").getOrThrow()?.sortOrder, "något som redan finns behåller sin plats")
    }

    @Test
    fun `utan lista att läsa blir platsen 0 i stället för att vänta`() = runTest {
        val persons = FakeCollectionFactory(scope = TestHouseholdScope(householdId = null)).persons()
        assertEquals(0, persons.nextSortOrder())
    }
}
