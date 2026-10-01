@file:OptIn(ExperimentalMaterial3Api::class)

package se.partee71.reseapoteket.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.ui.common.EditorEffect
import se.partee71.reseapoteket.ui.common.EditorUiState
import se.partee71.reseapoteket.ui.common.toMessage
import se.partee71.reseapoteket.ui.theme.Spacing

/** Radering i redigeringens meny – alltid bekräftad med [ConfirmDialog] (destructive). */
data class DeleteAction(val title: String, val message: String, val onConfirm: () -> Unit)

/**
 * Den enda redigeringsskärmen (NFR-2, skill shared-ui-components), tillsammans med
 * `EditorState`:
 * - "Spara" ([saveLabel]) aktiv först när formuläret är giltigt och ändrat;
 * - bakåt med osparade ändringar → "Släng ändringar?";
 * - sparfel → snackbar via `DataError.toMessage()`; lyckad sparning → [onClose];
 * - arkivera/radera i menyn när [onArchive]/[delete] finns;
 * - läsfel → feltillstånd med "Försök igen" ([onRetry]).
 *
 * @param onClose lämnar skärmen (navigationens tillbaka).
 */
@Composable
fun EntityEditScreen(
    title: String,
    state: EditorUiState<*>,
    effects: Flow<EditorEffect>,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    saveLabel: String = stringResource(R.string.save),
    onArchive: (() -> Unit)? = null,
    delete: DeleteAction? = null,
    onRetry: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val close by rememberUpdatedState(onClose)
    val context = LocalContext.current
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                EditorEffect.Done -> close()
                // Egen coroutine: en visad snackbar får inte hålla tillbaka nästa händelse.
                is EditorEffect.Failed -> launch { snackbar.showSnackbar(context.getString(effect.error.toMessage())) }
            }
        }
    }
    val back = { if (state.isDirty) confirmDiscard = true else onClose() }
    BackHandler(enabled = state.isDirty) { confirmDiscard = true }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(title, size = TopBarSize.Small, onBack = back) {
                AppButton(saveLabel, onSave, Modifier.padding(horizontal = Spacing.xs), enabled = state.canSave, loading = state.saving, compact = true)
                val menu = listOfNotNull(
                    onArchive?.let { AppMenuItem(stringResource(R.string.archive), it, R.drawable.ic_archive) },
                    delete?.let { AppMenuItem(stringResource(R.string.delete), { confirmDelete = true }, R.drawable.ic_delete, destructive = true) },
                )
                if (menu.isNotEmpty()) AppMenu(menu)
            }
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val loadError = state.loadError
            if (state.loading) {
                AppLoading()
            } else if (loadError != null) {
                EmptyState(
                    icon = R.drawable.ic_wifi_off,
                    title = stringResource(R.string.load_error_title),
                    message = stringResource(loadError.toMessage()),
                    isError = true,
                    action = { AppButton(stringResource(R.string.retry), onRetry, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary) },
                )
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
                        .padding(horizontal = Spacing.xl, vertical = Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.l),
                    content = content,
                )
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.discard_title),
            message = stringResource(R.string.discard_message),
            confirmLabel = stringResource(R.string.discard_confirm),
            onConfirm = {
                confirmDiscard = false
                onClose()
            },
            onDismiss = { confirmDiscard = false },
            dismissLabel = stringResource(R.string.keep_editing),
        )
    }
    if (confirmDelete && delete != null) {
        ConfirmDialog(
            title = delete.title,
            message = delete.message,
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                confirmDelete = false
                delete.onConfirm()
            },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}
