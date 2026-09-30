package se.partee71.reseapoteket.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
    val entries = remember(backStack) { appEntries(backStack) }
    AppNavHost(backStack, entries, modifier, onExit = { activity?.finish() })
}

/** Vilken skärm varje nyckel visar. Nycklar utan egen skärm än visar "Kommer snart". */
fun appEntries(backStack: AppBackStack): (AppKey) -> NavEntry<AppKey> =
    entryProvider(fallback = { key -> NavEntry(key) { ComingSoonScreen(onBack = { backStack.pop() }) } }) {
        entry<TripsKey> {
            TripsScreen(onAdd = { backStack.push(TripEditKey()) }, onOpen = { backStack.push(PackingListKey(it)) })
        }
        entry<PersonsKey> {
            PersonsScreen(onAdd = { backStack.push(PersonEditKey()) }, onOpen = { backStack.push(PersonDetailKey(it)) })
        }
        entry<FamilyItemsKey> {
            FamilyItemsScreen(onAdd = { backStack.push(FamilyItemEditKey()) }, onOpen = { backStack.push(FamilyItemEditKey(it)) })
        }
        entry<SettingsKey> {
            SettingsScreen(onOpenGallery = if (BuildConfig.DEBUG) ({ backStack.push(ComponentGalleryKey) }) else null)
        }
        entry<ComponentGalleryKey> { ComponentGallery(onBack = { backStack.pop() }) }
    }
