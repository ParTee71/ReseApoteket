package se.partee71.reseapoteket.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.partee71.reseapoteket.BuildConfig
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.data.datastore.SettingsStore
import se.partee71.reseapoteket.data.datastore.ThemeMode
import se.partee71.reseapoteket.ui.common.STOP_TIMEOUT_MILLIS
import se.partee71.reseapoteket.ui.components.ApotekCard
import se.partee71.reseapoteket.ui.components.AppDivider
import se.partee71.reseapoteket.ui.components.AppSegmentedChoice
import se.partee71.reseapoteket.ui.components.AppTopBar
import se.partee71.reseapoteket.ui.components.ItemRow
import se.partee71.reseapoteket.ui.components.LocalBottomClearance
import se.partee71.reseapoteket.ui.theme.AppTypography
import se.partee71.reseapoteket.ui.theme.Spacing

sealed interface SettingsEvent {
    data class SetTheme(val mode: ThemeMode) : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(private val settings: SettingsStore) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ThemeMode.System)

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SetTheme -> viewModelScope.launch { settings.setThemeMode(event.mode) }
        }
    }
}

/** Fliken Inställningar (SET-1). Hushåll, konto och backup kommer i #27. */
@Composable
fun SettingsScreen(onOpenGallery: (() -> Unit)?, viewModel: SettingsViewModel = hiltViewModel()) {
    val mode by viewModel.themeMode.collectAsStateWithLifecycle()
    SettingsContent(mode, viewModel::onEvent, BuildConfig.VERSION_NAME, onOpenGallery)
}

/** Temaalternativen i den ordning de visas: Ljust · Mörkt · System. */
private val THEME_OPTIONS = listOf(ThemeMode.Light to R.string.theme_light, ThemeMode.Dark to R.string.theme_dark, ThemeMode.System to R.string.theme_system)

/** @param onOpenGallery "Komponenter" – bara i debug-bygget, annars `null`. */
@Composable
fun SettingsContent(
    themeMode: ThemeMode,
    onEvent: (SettingsEvent) -> Unit,
    versionName: String,
    onOpenGallery: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar(stringResource(R.string.tab_settings))
        Column(Modifier.padding(horizontal = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Caption(stringResource(R.string.settings_appearance))
            ApotekCard {
                Column(Modifier.padding(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(stringResource(R.string.settings_theme), style = AppTypography.itemTitle)
                    AppSegmentedChoice(
                        options = THEME_OPTIONS.map { stringResource(it.second) },
                        selectedIndex = THEME_OPTIONS.indexOfFirst { it.first == themeMode },
                        onSelect = { onEvent(SettingsEvent.SetTheme(THEME_OPTIONS[it].first)) },
                    )
                }
            }
            Caption(stringResource(R.string.settings_about))
            ApotekCard {
                ItemRow(stringResource(R.string.app_name), subtitle = stringResource(R.string.settings_version_format, versionName))
                if (onOpenGallery != null) {
                    AppDivider()
                    ItemRow(
                        title = stringResource(R.string.settings_components),
                        subtitle = stringResource(R.string.settings_components_note),
                        onClick = onOpenGallery,
                        navigates = true,
                    )
                }
            }
            Spacer(Modifier.height(LocalBottomClearance.current))
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text.uppercase(),
        style = AppTypography.caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.s).semantics { heading() },
    )
}
