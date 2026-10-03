package se.partee71.reseapoteket.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import se.partee71.reseapoteket.BuildConfig
import se.partee71.reseapoteket.ui.components.ComponentGallery
import se.partee71.reseapoteket.ui.familyitems.FamilyItemEditScreen
import se.partee71.reseapoteket.ui.familyitems.FamilyItemEditViewModel
import se.partee71.reseapoteket.ui.familyitems.FamilyItemsScreen
import se.partee71.reseapoteket.ui.packing.PackingListScreen
import se.partee71.reseapoteket.ui.packing.PackingListViewModel
import se.partee71.reseapoteket.ui.persons.PersonDetailScreen
import se.partee71.reseapoteket.ui.persons.PersonDetailViewModel
import se.partee71.reseapoteket.ui.persons.PersonEditScreen
import se.partee71.reseapoteket.ui.persons.PersonEditViewModel
import se.partee71.reseapoteket.ui.persons.PersonsScreen
import se.partee71.reseapoteket.ui.regimens.RegimenEditScreen
import se.partee71.reseapoteket.ui.regimens.RegimenEditViewModel
import se.partee71.reseapoteket.ui.regimens.RegimenKindConfig
import se.partee71.reseapoteket.ui.settings.HouseholdEditScreen
import se.partee71.reseapoteket.ui.settings.SettingsScreen
import se.partee71.reseapoteket.ui.sync.SyncViewModel
import se.partee71.reseapoteket.ui.trips.TripEditScreen
import se.partee71.reseapoteket.ui.trips.TripEditViewModel
import se.partee71.reseapoteket.ui.trips.TripsScreen

/** Appens innehåll efter inloggning: fyra flikar med egna back stackar och synkläget (NFR-6). */
@Composable
fun AppNavigation(modifier: Modifier = Modifier, syncViewModel: SyncViewModel = hiltViewModel()) {
    val sync by syncViewModel.state.collectAsStateWithLifecycle()
    val backStack = rememberAppBackStack()
    val activity = LocalActivity.current
    val tabOwner = checkNotNull(LocalViewModelStoreOwner.current) { "AppNavigation kräver en ViewModelStoreOwner" }
    val entries = remember(backStack, tabOwner) { appEntries(backStack, tabOwner) }
    AppNavHost(backStack, entries, modifier, onExit = { activity?.finish() }, sync = sync, onSyncEvent = syncViewModel::onEvent)
}

/**
 * Vilken skärm varje nyckel visar – varje nyckel har en (`AppNavigationTest`).
 * Flikarnas ViewModels hämtas från [tabOwner] (aktiviteten) i stället för navigationsposten,
 * så att en flik behåller sin lista när man byter flik (NAV-2); undersidornas ViewModels hör
 * till sin post och försvinner när den stängs. En skärm stänger sig med `popIfTop(key)`, aldrig
 * `pop()`, så att ett andra tryck inte stänger skärmen under.
 */
fun appEntries(backStack: AppBackStack, tabOwner: ViewModelStoreOwner): (AppKey) -> NavEntry<AppKey> =
    // Varje nyckel har sin skärm (AppKey är förseglad); en saknad är ett programfel.
    entryProvider(fallback = { key -> error("Ingen skärm för $key") }) {
        entry<TripsKey> {
            TripsScreen(onAdd = { backStack.push(TripEditKey()) }, onOpen = { backStack.push(PackingListKey(it)) }, viewModel = hiltViewModel(tabOwner))
        }
        entry<PersonsKey> {
            PersonsScreen(onAdd = { backStack.push(PersonEditKey()) }, onOpen = { backStack.push(PersonDetailKey(it)) }, viewModel = hiltViewModel(tabOwner))
        }
        entry<FamilyItemsKey> {
            FamilyItemsScreen(
                onAdd = { backStack.push(FamilyItemEditKey()) },
                onAddExample = { backStack.push(FamilyItemEditKey(example = it)) },
                onOpen = { backStack.push(FamilyItemEditKey(it)) },
                viewModel = hiltViewModel(tabOwner),
            )
        }
        entry<SettingsKey> {
            SettingsScreen(
                onEditHousehold = { backStack.push(HouseholdEditKey) },
                onOpenGallery = if (BuildConfig.DEBUG) ({ backStack.push(ComponentGalleryKey) }) else null,
                viewModel = hiltViewModel(tabOwner),
            )
        }
        entry<PersonDetailKey> { key ->
            PersonDetailScreen(
                onBack = { backStack.popIfTop(key) },
                onEdit = { backStack.push(PersonEditKey(key.personId)) },
                onAddRegimen = { kind -> backStack.push(RegimenEditKey(key.personId, kind)) },
                onOpenRegimen = { regimen -> backStack.push(RegimenEditKey(key.personId, regimen.kind, regimen.id)) },
                viewModel = hiltViewModel<PersonDetailViewModel, PersonDetailViewModel.Factory> { it.create(key.personId) },
            )
        }
        entry<HouseholdEditKey> { key ->
            HouseholdEditScreen(onClose = { backStack.popIfTop(key) }, viewModel = hiltViewModel())
        }
        entry<PersonEditKey> { key ->
            PersonEditScreen(
                onClose = { backStack.popIfTop(key) },
                viewModel = hiltViewModel<PersonEditViewModel, PersonEditViewModel.Factory> { it.create(key.personId) },
            )
        }
        entry<RegimenEditKey> { key ->
            RegimenEditScreen(
                RegimenKindConfig.of(key.kind).editor,
                onClose = { backStack.popIfTop(key) },
                viewModel = hiltViewModel<RegimenEditViewModel, RegimenEditViewModel.Factory> { it.create(key.personId, key.kind, key.regimenId) },
            )
        }
        entry<FamilyItemEditKey> { key ->
            val exampleName = key.example?.let { stringResource(it.label) }.orEmpty()
            FamilyItemEditScreen(
                onClose = { backStack.popIfTop(key) },
                viewModel = hiltViewModel<FamilyItemEditViewModel, FamilyItemEditViewModel.Factory> { it.create(key.itemId, key.example, exampleName) },
            )
        }
        entry<TripEditKey> { key ->
            TripEditScreen(
                onClose = { backStack.popIfTop(key) },
                // En ny resa öppnar sin packlista i stället för att gå tillbaka (RES-3).
                onCreated = { id ->
                    backStack.popIfTop(key)
                    backStack.push(PackingListKey(id))
                },
                viewModel = hiltViewModel<TripEditViewModel, TripEditViewModel.Factory> { it.create(key.tripId) },
            )
        }
        entry<PackingListKey> { key ->
            PackingListScreen(
                onBack = { backStack.popIfTop(key) },
                // Resan ändras från packlistan (RES-1).
                onEditTrip = { backStack.push(TripEditKey(key.tripId)) },
                viewModel = hiltViewModel<PackingListViewModel, PackingListViewModel.Factory> { it.create(key.tripId) },
            )
        }
        entry<ComponentGalleryKey> { key -> ComponentGallery(onBack = { backStack.popIfTop(key) }) }
    }
