package se.partee71.reseapoteket.ui.tabs

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import se.partee71.reseapoteket.core.model.FamilyItem
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.Trip
import se.partee71.reseapoteket.data.FakeCollectionFactory
import se.partee71.reseapoteket.data.repository.DefaultFamilyItemRepository
import se.partee71.reseapoteket.data.repository.DefaultPackingRepository
import se.partee71.reseapoteket.data.repository.DefaultPersonRepository
import se.partee71.reseapoteket.data.repository.DefaultRegimenRepository
import se.partee71.reseapoteket.data.repository.DefaultTripRepository
import se.partee71.reseapoteket.testing.MainDispatcherRule
import se.partee71.reseapoteket.ui.common.ListUiState
import se.partee71.reseapoteket.ui.familyitems.FamilyItemsViewModel
import se.partee71.reseapoteket.ui.persons.PersonsViewModel
import se.partee71.reseapoteket.ui.trips.TripsViewModel

/** Flikarnas ViewModels visar samlingens aktiva dokument; arkiverade döljs. */
class TabViewModelsTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val collections = FakeCollectionFactory()

    @Test
    fun `Personer visar aktiva personer i ordning`() = runTest {
        val repository = DefaultPersonRepository(collections)
        repository.upsert(Person("p2", "Erik", sortOrder = 2))
        repository.upsert(Person("p1", "Anna", sortOrder = 1))
        repository.upsert(Person("p3", "Bo", archived = true))
        PersonsViewModel(repository, DefaultRegimenRepository(collections)).state.test {
            assertEquals(listOf("Anna", "Erik"), (expectMostRecentItem() as ListUiState.Content).items.map { it.person.name })
        }
    }

    @Test
    fun `Resor och Familjeartiklar är tomma utan dokument`() = runTest {
        TripsViewModel(DefaultTripRepository(collections), DefaultPersonRepository(collections), DefaultPackingRepository(collections)).state.test { assertEquals(ListUiState.Empty, expectMostRecentItem()) }
        FamilyItemsViewModel(DefaultFamilyItemRepository(collections)).state.test { assertEquals(ListUiState.Empty, expectMostRecentItem()) }
    }

    @Test
    fun `Resor och Familjeartiklar visar sina dokument`() = runTest {
        DefaultTripRepository(collections).upsert(Trip("t1", "Kreta"))
        DefaultFamilyItemRepository(collections).upsert(FamilyItem("f1", "Solkräm"))
        TripsViewModel(DefaultTripRepository(collections), DefaultPersonRepository(collections), DefaultPackingRepository(collections)).state.test {
            assertEquals(listOf("Kreta"), (expectMostRecentItem() as ListUiState.Content).items.map { it.trip.name })
        }
        FamilyItemsViewModel(DefaultFamilyItemRepository(collections)).state.test {
            assertEquals(listOf("Solkräm"), (expectMostRecentItem() as ListUiState.Content).items.map { it.name })
        }
    }
}
