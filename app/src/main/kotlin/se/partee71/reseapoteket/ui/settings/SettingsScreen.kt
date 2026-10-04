package se.partee71.reseapoteket.ui.settings

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.partee71.reseapoteket.BuildConfig
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.model.Household
import se.partee71.reseapoteket.core.model.HouseholdSettings
import se.partee71.reseapoteket.core.schema.HouseholdCodec
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.data.auth.AuthRepository
import se.partee71.reseapoteket.data.auth.AuthUser
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.dataError
import se.partee71.reseapoteket.data.common.withFallback
import se.partee71.reseapoteket.data.datastore.SettingsStore
import se.partee71.reseapoteket.data.datastore.ThemeMode
import se.partee71.reseapoteket.data.export.ExportFiles
import se.partee71.reseapoteket.data.export.HouseholdExport
import se.partee71.reseapoteket.data.medicines.MedicineRepository
import se.partee71.reseapoteket.data.repository.HouseholdRepository
import se.partee71.reseapoteket.ui.common.DateFormat
import se.partee71.reseapoteket.ui.common.STOP_TIMEOUT_MILLIS
import se.partee71.reseapoteket.ui.common.toMessage
import se.partee71.reseapoteket.ui.components.ApotekCard
import se.partee71.reseapoteket.ui.components.AppDivider
import se.partee71.reseapoteket.ui.components.AppSegmentedChoice
import se.partee71.reseapoteket.ui.components.AppTopBar
import se.partee71.reseapoteket.ui.components.ConfirmDialog
import se.partee71.reseapoteket.ui.components.FieldError
import se.partee71.reseapoteket.ui.components.GroupLabel
import se.partee71.reseapoteket.ui.components.ItemRow
import se.partee71.reseapoteket.ui.components.LocalBottomClearance
import se.partee71.reseapoteket.ui.components.PersonAvatar
import se.partee71.reseapoteket.ui.components.QuantityStepper
import se.partee71.reseapoteket.ui.components.SwitchRow
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.reseapoteket.ui.theme.Spacing

/** Kortet där en åtgärd gjordes – ett fel visas där (NFR-5). */
enum class SettingsSection { Household, Account, Data }

data class SettingsError(val section: SettingsSection, val error: DataError)

data class SettingsUiState(
    val household: Household? = null,
    val account: AuthUser? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    val moveCheckedLast: Boolean = true,
    val exporting: Boolean = false,
    val error: SettingsError? = null,
)

sealed interface SettingsEvent {
    data class SetTheme(val mode: ThemeMode) : SettingsEvent

    data class SetMoveCheckedLast(val on: Boolean) : SettingsEvent

    data class SetMarginDays(val days: Int) : SettingsEvent

    /** Efter bekräftelsen (SET-4). */
    data object SignOut : SettingsEvent

    data object Export : SettingsEvent
}

/** Exportfilen är sparad – delningsmenyn öppnas med dess `content://`-adress (SET-5). */
data class ShareExport(val uri: String)

/**
 * Fliken Inställningar (SET-1–6): hushållets marginaldagar skrivs direkt (bara det fältet),
 * namnet ändras i ett eget formulär; tema och "Flytta avprickade sist" sparas på enheten.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val households: HouseholdRepository,
    private val auth: AuthRepository,
    private val export: HouseholdExport,
    private val files: ExportFiles,
    private val clock: Clock,
    private val timeZone: TimeZone,
    medicines: MedicineRepository,
) : ViewModel() {

    /** Datumet för den inbyggda läkemedelslistan (REC-6), eller `null` om den saknas. */
    val medicinesUpdated: StateFlow<LocalDate?> = flow { emit(medicines.catalog().updated) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val action = MutableStateFlow(Action())

    // Hushållet direkt ur cachen: en skrivning läggs där genast (offline först), så nästa tryck på
    // +/− räknar redan från det nya värdet – och en ändring från en annan enhet syns som den är.
    val state: StateFlow<SettingsUiState> = combine(
        households.current.withFallback(null),
        auth.authState.withFallback(null),
        settings.themeMode,
        settings.moveCheckedLast.withFallback(true),
        action,
    ) { household, account, theme, checkedLast, action ->
        SettingsUiState(household, account, theme, checkedLast, action.exporting, action.error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    private val _shares = Channel<ShareExport>(Channel.CONFLATED)

    /** En gång per export – delningsmenyn visas inte igen vid nästa visning. */
    val shares: Flow<ShareExport> = _shares.receiveAsFlow()

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SetTheme -> viewModelScope.launch { settings.setThemeMode(event.mode) }
            is SettingsEvent.SetMoveCheckedLast -> viewModelScope.launch { settings.setMoveCheckedLast(event.on) }
            is SettingsEvent.SetMarginDays -> setMarginDays(event.days)
            SettingsEvent.SignOut -> run(SettingsSection.Account) { auth.signOut() }
            SettingsEvent.Export -> exportHousehold()
        }
    }

    /** Bara marginalfältet skrivs – ett namn som ändrats på en annan enhet står kvar. */
    private fun setMarginDays(days: Int) {
        val household = state.value.household ?: return
        val margin = days.coerceIn(HouseholdSettings.MARGIN_DAYS_RANGE)
        if (margin == household.marginDays) return
        run(SettingsSection.Household) { households.update(household.copy(marginDays = margin), setOf(HouseholdCodec.MARGIN_DAYS)) }
    }

    private fun exportHousehold() {
        if (action.value.exporting) return
        action.update { Action(exporting = true) }
        viewModelScope.launch {
            val today = clock.now().toLocalDateTime(timeZone).date
            val result = export.export().mapCatching { text -> files.save("reseapoteket-$today.json", text).getOrThrow() }
            result.onSuccess { _shares.trySend(ShareExport(it)) }
            // Ett fel från en annan åtgärd under tiden står kvar.
            val failed = result.dataError()?.let { SettingsError(SettingsSection.Data, it) }
            action.update { Action(exporting = false, error = failed ?: it.error) }
        }
    }

    /** Kör en åtgärd; ett tidigare fel försvinner, ett nytt visas i kortet där åtgärden gjordes. */
    private fun run(section: SettingsSection, block: suspend () -> Result<Unit>) {
        action.update { it.copy(error = null) }
        viewModelScope.launch {
            block().dataError()?.let { error -> action.update { it.copy(error = SettingsError(section, error)) } }
        }
    }

    private data class Action(val exporting: Boolean = false, val error: SettingsError? = null)
}

@Composable
fun SettingsScreen(
    onEditHousehold: () -> Unit,
    onOpenGallery: (() -> Unit)?,
    viewModel: SettingsViewModel = hiltViewModel(),
    remindersViewModel: RemindersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val medicinesUpdated by viewModel.medicinesUpdated.collectAsStateWithLifecycle()
    val reminders by remindersViewModel.state.collectAsStateWithLifecycle()
    val access = rememberNotificationAccess()
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.settings_export_chooser)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Bara medan skärmen syns: i bakgrunden får appen inte öppna delningsmenyn, och händelsen
    // väntar då i stället för att gå förlorad.
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { collectShares(viewModel, context, chooserTitle) }
    }
    SettingsContent(state, viewModel::onEvent, BuildConfig.VERSION_NAME, onEditHousehold, onOpenGallery, medicinesUpdated = medicinesUpdated) {
        RemindersCard(reminders, remindersViewModel::onEvent, access.allowed, access.ask, access.openSettings)
    }
}

/** Delningsmenyn för varje exportfil – bara på användarens tryck, och bara den filen får läsas (SET-5). */
private suspend fun collectShares(viewModel: SettingsViewModel, context: Context, chooserTitle: String) {
    viewModel.shares.collect { share ->
        val uri = share.uri.toUri()
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = ClipData.newRawUri(null, uri) }
        context.startActivity(Intent.createChooser(send, chooserTitle))
    }
}

/** Temaalternativen i den ordning de visas: Ljust · Mörkt · System. */
private val THEME_OPTIONS = listOf(ThemeMode.Light to R.string.theme_light, ThemeMode.Dark to R.string.theme_dark, ThemeMode.System to R.string.theme_system)

/**
 * @param onOpenGallery "Komponenter" – bara i debug-bygget, annars `null`.
 * @param medicinesUpdated datumet för den inbyggda läkemedelslistan (REC-6), under Om.
 * @param reminders kortet Påminnelser (PÅM-1), efter Packlista.
 */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    versionName: String,
    onEditHousehold: () -> Unit,
    onOpenGallery: (() -> Unit)?,
    modifier: Modifier = Modifier,
    medicinesUpdated: LocalDate? = null,
    reminders: (@Composable () -> Unit)? = null,
) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val sourceUrl = stringResource(R.string.settings_source_url)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar(stringResource(R.string.tab_settings))
        Column(Modifier.padding(horizontal = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            state.household?.let { household ->
                GroupLabel(stringResource(R.string.settings_household))
                SettingsCard(state.error, SettingsSection.Household) {
                    ItemRow(
                        title = household.name.ifBlank { stringResource(R.string.settings_household) },
                        subtitle = stringResource(R.string.settings_household_name),
                        onClick = onEditHousehold,
                        navigates = true,
                    )
                    AppDivider()
                    ItemRow(
                        title = stringResource(R.string.settings_margin_days),
                        subtitle = stringResource(R.string.settings_margin_days_note),
                        below = {
                            QuantityStepper(
                                household.marginDays,
                                { onEvent(SettingsEvent.SetMarginDays(it)) },
                                stringResource(R.string.settings_margin_days),
                                range = HouseholdSettings.MARGIN_DAYS_RANGE,
                            )
                        },
                    )
                }
            }
            GroupLabel(stringResource(R.string.settings_packing))
            ApotekCard {
                SwitchRow(
                    stringResource(R.string.settings_move_checked_last),
                    state.moveCheckedLast,
                    { onEvent(SettingsEvent.SetMoveCheckedLast(it)) },
                    subtitle = stringResource(R.string.settings_move_checked_last_note),
                )
            }
            reminders?.let {
                GroupLabel(stringResource(R.string.reminders_title))
                it()
            }
            GroupLabel(stringResource(R.string.settings_appearance))
            ApotekCard {
                ItemRow(
                    title = stringResource(R.string.settings_theme),
                    below = {
                        AppSegmentedChoice(
                            options = THEME_OPTIONS.map { stringResource(it.second) },
                            selectedIndex = THEME_OPTIONS.indexOfFirst { it.first == state.themeMode },
                            onSelect = { onEvent(SettingsEvent.SetTheme(THEME_OPTIONS[it].first)) },
                        )
                    },
                )
            }
            GroupLabel(stringResource(R.string.settings_account))
            SettingsCard(state.error, SettingsSection.Account) {
                state.account?.let { account ->
                    val name = account.name ?: account.email ?: stringResource(R.string.settings_signed_in)
                    ItemRow(
                        title = name,
                        subtitle = account.email?.takeIf { it != name } ?: stringResource(R.string.settings_signed_in),
                        leading = { PersonAvatar(name.take(1).uppercase(), AppColors.person(0)) },
                    )
                    AppDivider()
                }
                ItemRow(stringResource(R.string.sign_out), onClick = { confirmSignOut = true })
            }
            GroupLabel(stringResource(R.string.settings_data))
            SettingsCard(state.error, SettingsSection.Data) {
                ItemRow(
                    title = stringResource(R.string.settings_sync_title),
                    subtitle = stringResource(R.string.settings_sync_note),
                )
                ItemRow(
                    title = stringResource(R.string.settings_data_version),
                    subtitle = stringResource(R.string.settings_data_format, state.household?.schemaVersion ?: Schema.FIRST_VERSION, versionName),
                )
                AppDivider()
                ItemRow(
                    title = stringResource(R.string.settings_export),
                    subtitle = stringResource(if (state.exporting) R.string.settings_export_busy else R.string.settings_export_note),
                    onClick = { onEvent(SettingsEvent.Export) }.takeUnless { state.exporting },
                    navigates = true,
                )
            }
            GroupLabel(stringResource(R.string.settings_about))
            ApotekCard {
                ItemRow(stringResource(R.string.app_name), subtitle = stringResource(R.string.settings_version_format, versionName))
                medicinesUpdated?.let {
                    AppDivider()
                    ItemRow(stringResource(R.string.settings_medicines), subtitle = stringResource(R.string.settings_medicines_note, DateFormat.display(it)))
                }
                AppDivider()
                ItemRow(
                    title = stringResource(R.string.settings_source),
                    subtitle = stringResource(R.string.settings_source_note),
                    onClick = { uriHandler.openUri(sourceUrl) },
                    navigates = true,
                )
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

    if (confirmSignOut) {
        ConfirmDialog(
            title = stringResource(R.string.sign_out_title),
            message = stringResource(R.string.sign_out_message),
            confirmLabel = stringResource(R.string.sign_out),
            onConfirm = {
                confirmSignOut = false
                onEvent(SettingsEvent.SignOut)
            },
            onDismiss = { confirmSignOut = false },
            destructive = true,
        )
    }
}

/** Ett kort; ett fel från en åtgärd i just det kortet visas sist i det. */
@Composable
private fun SettingsCard(error: SettingsError?, section: SettingsSection, content: @Composable () -> Unit) {
    ApotekCard {
        content()
        error?.takeIf { it.section == section }?.let { FieldError(stringResource(it.error.toMessage()), Modifier.padding(Spacing.s)) }
    }
}
