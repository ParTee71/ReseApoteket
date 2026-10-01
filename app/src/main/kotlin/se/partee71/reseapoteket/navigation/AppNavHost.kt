package se.partee71.reseapoteket.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.ui.components.AppFloatingToolbar
import se.partee71.reseapoteket.ui.components.FloatingToolbarClearance
import se.partee71.reseapoteket.ui.components.LocalBottomClearance
import se.partee71.reseapoteket.ui.components.ToolbarItem
import se.partee71.reseapoteket.ui.theme.Spacing

/**
 * Navigationsramen: `NavDisplay` med [Transitions] och den flytande verktygsraden, som bara
 * syns i en fliks rot. Skärmarna kommer från [entryProvider] (`appEntries` i appen); flikarnas
 * ViewModels hör till aktiviteten (se `appEntries`), så att de också överlever flikbyten.
 */
@Composable
fun AppNavHost(
    backStack: AppBackStack,
    entryProvider: (AppKey) -> NavEntry<AppKey>,
    modifier: Modifier = Modifier,
    onExit: () -> Unit = {},
) {
    val toolbar = toolbarItems()
    Box(modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBottomClearance provides if (backStack.atTopLevel) FloatingToolbarClearance else 0.dp) {
            NavDisplay(
                backStack = backStack.entries,
                onBack = { if (!backStack.pop()) onExit() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(rememberTabRootStateHolder()),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                transitionSpec = Transitions.forward,
                popTransitionSpec = Transitions.back,
                predictivePopTransitionSpec = Transitions.predictiveBack,
                entryProvider = { key -> entryProvider(key).asTabRootIf(key) },
            )
        }
        AnimatedVisibility(
            visible = backStack.atTopLevel,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = Spacing.l),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            AppFloatingToolbar(
                items = toolbar,
                selectedIndex = TOP_LEVEL.indexOf(backStack.currentTab),
                onSelect = { backStack.select(TOP_LEVEL[it]) },
            )
        }
    }
}

/**
 * Sparat tillstånd (t.ex. rullning) för flikarnas rötter behålls när fliken lämnar back stacken
 * vid flikbyte (NAV-2); undersidornas tillstånd släpps som vanligt när de stängs.
 */
@Composable
private fun rememberTabRootStateHolder(): SaveableStateHolder {
    val holder = rememberSaveableStateHolder()
    return remember(holder) { TabRootStateHolder(holder) }
}

private class TabRootStateHolder(private val holder: SaveableStateHolder) : SaveableStateHolder {
    @Composable
    override fun SaveableStateProvider(key: Any, content: @Composable () -> Unit) = holder.SaveableStateProvider(key, content)

    override fun removeState(key: Any) {
        if (!(key is String && key.startsWith(TAB_ROOT))) holder.removeState(key)
    }
}

/** Prefix för flikrötternas innehållsnycklar (en sträng, eftersom nyckeln sparas i en Bundle). */
private const val TAB_ROOT = "flikrot:"

private fun NavEntry<AppKey>.asTabRootIf(key: AppKey): NavEntry<AppKey> =
    if (key is TopLevelKey) NavEntry(key, contentKey = TAB_ROOT + key, metadata = metadata) { Content() } else this

@Composable
private fun toolbarItems(): List<ToolbarItem> = listOf(
    ToolbarItem(stringResource(R.string.tab_trips), R.drawable.ic_luggage),
    ToolbarItem(stringResource(R.string.tab_persons), R.drawable.ic_person),
    ToolbarItem(stringResource(R.string.tab_family_items), R.drawable.ic_family_items),
    ToolbarItem(stringResource(R.string.tab_settings), R.drawable.ic_settings),
)
