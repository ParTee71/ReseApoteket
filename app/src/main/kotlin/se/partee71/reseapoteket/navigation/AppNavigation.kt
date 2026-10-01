package se.partee71.reseapoteket.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import se.partee71.reseapoteket.BuildConfig
import se.partee71.reseapoteket.ui.components.ComponentGallery
import se.partee71.reseapoteket.ui.familyitems.FamilyItemsScreen
import se.partee71.reseapoteket.ui.persons.PersonsScreen
import se.partee71.reseapoteket.ui.placeholder.ComingSoonScreen
import se.partee71.reseapoteket.ui.settings.SettingsScreen
import se.partee71.reseapoteket.ui.trips.TripsScreen

/** Appens innehåll efter inloggning: fyra flikar med egna back stackar. */
@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val backStack = rememberAppBackStack()
    val activity = LocalActivity.current
    val tabOwner = checkNotNull(LocalViewModelStoreOwner.current) { "AppNavigation kräver en ViewModelStoreOwner" }
    val entries = remember(backStack, tabOwner) { appEntries(backStack, tabOwner) }
    AppNavHost(backStack, entries, modifier, onExit = { activity?.finish() })
}

/**
 * Vilken skärm varje nyckel visar. Nycklar utan egen skärm än visar "Kommer snart".
 * Flikarnas ViewModels hämtas från [tabOwner] (aktiviteten) i stället för navigationsposten,
 * så att en flik behåller sin lista när man byter flik (NAV-2); undersidornas ViewModels hör
 * till sin post och försvinner när den stängs.
 */
fun appEntries(backStack: AppBackStack, tabOwner: ViewModelStoreOwner): (AppKey) -> NavEntry<AppKey> =
    entryProvider(fallback = { key -> NavEntry(key) { ComingSoonScreen(onBack = { backStack.pop() }) } }) {
        entry<TripsKey> {
            TripsScreen(onAdd = { backStack.push(TripEditKey()) }, onOpen = { backStack.push(PackingListKey(it)) }, viewModel = hiltViewModel(tabOwner))
        }
        entry<PersonsKey> {
            PersonsScreen(onAdd = { backStack.push(PersonEditKey()) }, onOpen = { backStack.push(PersonDetailKey(it)) }, viewModel = hiltViewModel(tabOwner))
        }
        entry<FamilyItemsKey> {
            FamilyItemsScreen(
                onAdd = { backStack.push(FamilyItemEditKey()) },
                onOpen = { backStack.push(FamilyItemEditKey(it)) },
                viewModel = hiltViewModel(tabOwner),
            )
        }
        entry<SettingsKey> {
            SettingsScreen(
                onOpenGallery = if (BuildConfig.DEBUG) ({ backStack.push(ComponentGalleryKey) }) else null,
                viewModel = hiltViewModel(tabOwner),
            )
        }
        entry<ComponentGalleryKey> { ComponentGallery(onBack = { backStack.pop() }) }
    }
