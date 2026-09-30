@file:OptIn(ExperimentalMaterial3Api::class)

package se.partee71.reseapoteket.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.ui.common.ListUiState
import se.partee71.reseapoteket.ui.common.toMessage
import se.partee71.reseapoteket.ui.theme.Spacing
import se.partee71.reseapoteket.ui.theme.Tone

/** Det tomma tillståndet för en lista: ikon, rubrik, en mening och texten på lägg till-knappen. */
data class EmptyContent(
    @param:DrawableRes val icon: Int,
    val title: String,
    val message: String,
    val actionLabel: String,
)

/** En grupp i en grupperad lista: rubrik, valfri ikon och ton. */
data class ListGroup(val title: String, @param:DrawableRes val icon: Int? = null, val tone: Tone = Tone.Primary)

/** "Visa arkiverade" i listans meny, där arkivering finns. */
data class ArchivedToggle(val showing: Boolean, val onToggle: () -> Unit)

/**
 * Den enda listskärmen (NFR-1, skill shared-ui-components). Samma fyra lägen överallt:
 * laddning → [AppLoading]; tomt → [EmptyState] med knapp; fel → feltillstånd med
 * "Försök igen"; innehåll → rader i kort, valfritt grupperade med [SectionHeader].
 * "Lägg till" står nere till höger ovanför verktygsraden; arkiverade rader ångras med
 * [UndoSnackbar].
 *
 * @param group grupp för en rad; grupperna visas i den ordning de först förekommer.
 */
@Composable
fun <T> EntityListScreen(
    title: String,
    state: ListUiState<T>,
    empty: EmptyContent,
    onAdd: () -> Unit,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    addMenu: List<AppMenuItem> = emptyList(),
    group: ((T) -> ListGroup)? = null,
    archived: ArchivedToggle? = null,
    undo: UndoRequest? = null,
    onUndo: () -> Unit = {},
    onUndoDismissed: () -> Unit = {},
    row: @Composable (T) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val clearance = LocalBottomClearance.current
    UndoSnackbar(undo, snackbar, onUndo, onUndoDismissed)
    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(title, scrollBehavior = scroll) {
                archived?.let {
                    val label = stringResource(if (it.showing) R.string.hide_archived else R.string.show_archived)
                    AppMenu(listOf(AppMenuItem(label, it.onToggle, R.drawable.ic_unarchive)))
                }
            }
        },
        snackbarHost = { AppSnackbarHost(snackbar, Modifier.padding(bottom = clearance)) },
        floatingActionButton = {
            if (state is ListUiState.Content) {
                AddSplitButton(empty.actionLabel, onAdd, Modifier.padding(bottom = clearance), menuItems = addMenu)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                ListUiState.Loading -> AppLoading()
                ListUiState.Empty -> EmptyState(
                    icon = empty.icon,
                    title = empty.title,
                    message = empty.message,
                    modifier = Modifier.padding(bottom = clearance),
                    action = { AppButton(empty.actionLabel, onAdd, Modifier.fillMaxWidth(), icon = R.drawable.ic_add) },
                )
                is ListUiState.Error -> EmptyState(
                    icon = R.drawable.ic_wifi_off,
                    title = stringResource(R.string.list_error_title),
                    message = stringResource(state.error.toMessage()),
                    modifier = Modifier.padding(bottom = clearance),
                    isError = true,
                    action = { AppButton(stringResource(R.string.retry), onRetry, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary) },
                )
                is ListUiState.Content -> ListContent(state.items, key, group, clearance, row)
            }
        }
    }
}

@Composable
private fun <T> ListContent(
    items: List<T>,
    key: (T) -> Any,
    group: ((T) -> ListGroup)?,
    clearance: Dp,
    row: @Composable (T) -> Unit,
) {
    val groups = remember(items, group) { if (group == null) listOf(null to items) else items.groupBy(group).toList() }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, top = Spacing.xs, bottom = clearance + ADD_BUTTON_CLEARANCE),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        items(groups, key = { (header, rows) -> header?.title ?: key(rows.first()) }) { (header, rows) ->
            ApotekCard(Modifier.animateItem()) {
                header?.let { SectionHeader(it.title, icon = it.icon, count = rows.size.toString(), tone = it.tone) }
                rows.forEach { item -> Box(Modifier.fillMaxWidth()) { key(key(item)) { row(item) } } }
            }
        }
    }
}

/** Plats under sista raden så att lägg till-knappen inte täcker den. */
private val ADD_BUTTON_CLEARANCE = 80.dp
