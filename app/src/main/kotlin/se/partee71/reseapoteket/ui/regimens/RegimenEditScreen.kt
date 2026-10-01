package se.partee71.reseapoteket.ui.regimens

import androidx.annotation.StringRes
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.engine.PackingEngine
import se.partee71.reseapoteket.core.engine.PackingPreviewText
import se.partee71.reseapoteket.core.engine.UnitNames
import se.partee71.reseapoteket.core.model.DoseSchedule
import se.partee71.reseapoteket.core.model.HouseholdSettings
import se.partee71.reseapoteket.core.model.ItemUnit
import se.partee71.reseapoteket.core.model.MedicineForm
import se.partee71.reseapoteket.core.model.Regimen
import se.partee71.reseapoteket.core.model.RegimenKind
import se.partee71.reseapoteket.core.model.displayName
import se.partee71.reseapoteket.data.common.nextSortOrder
import se.partee71.reseapoteket.data.repository.HouseholdRepository
import se.partee71.reseapoteket.data.repository.RegimenRepository
import se.partee71.reseapoteket.ui.common.EditorEffect
import se.partee71.reseapoteket.ui.common.EditorState
import se.partee71.reseapoteket.ui.common.EditorUiState
import se.partee71.reseapoteket.ui.common.Validator
import se.partee71.reseapoteket.ui.components.AppFilterChip
import se.partee71.reseapoteket.ui.components.AppTextField
import se.partee71.reseapoteket.ui.components.ChipRow
import se.partee71.reseapoteket.ui.components.DeleteAction
import se.partee71.reseapoteket.ui.components.DoseScheduleRow
import se.partee71.reseapoteket.ui.components.EntityEditScreen
import se.partee71.reseapoteket.ui.components.LabeledGroup
import se.partee71.reseapoteket.ui.components.PackingPreview
import se.partee71.reseapoteket.ui.components.SwitchRow
import se.partee71.reseapoteket.ui.components.kindLabel

/** REC-1, REC-2: namnet är obligatoriskt och doseringen ska gå att räkna på. */
object RegimenValidator : Validator<Regimen> {
    const val NAME = "name"
    const val SCHEDULE = "schedule"
    const val PACKAGE_SIZE = "packageSize"

    override fun validate(value: Regimen): Map<String, Int> = buildMap {
        if (value.name.isBlank()) put(NAME, R.string.name_required)
        scheduleError(value.schedule)?.let { put(SCHEDULE, it) }
        value.packageSize?.let { if (it < 1) put(PACKAGE_SIZE, R.string.regimen_package_size_invalid) }
    }

    /** En okänd dosering (från en nyare app) kan inte ändras här och bevaras som den är (BCK-5). */
    @StringRes
    private fun scheduleError(schedule: DoseSchedule): Int? = when (schedule) {
        is DoseSchedule.Daily -> R.string.regimen_doses_required.takeIf { schedule.dosesPerDay < 1 }
        is DoseSchedule.Weekdays -> R.string.regimen_days_required.takeIf { schedule.days.isEmpty() }
        is DoseSchedule.IntervalDays -> R.string.regimen_interval_min.takeIf { schedule.intervalDays < 2 }
        is DoseSchedule.AsNeeded -> R.string.regimen_as_needed_required.takeIf { schedule.quantity < 1 }
        is DoseSchedule.Unknown -> null
    }
}

sealed interface RegimenEditEvent {
    data class Name(val name: String) : RegimenEditEvent

    data class Strength(val strength: String) : RegimenEditEvent

    data class Form(val form: MedicineForm) : RegimenEditEvent

    data class DoseUnit(val unit: ItemUnit) : RegimenEditEvent

    data class Schedule(val schedule: DoseSchedule) : RegimenEditEvent

    /** Förpackningsstorleken som den skrivs; bara siffror räknas, tomt = ingen. */
    data class PackageSize(val text: String) : RegimenEditEvent

    data class Notes(val notes: String) : RegimenEditEvent

    data class Active(val active: Boolean) : RegimenEditEvent

    data object Save : RegimenEditEvent

    data object Delete : RegimenEditEvent

    data object Retry : RegimenEditEvent
}

/** Skapa ([regimenId] = `null`) eller redigera ett recept eller kosttillskott hos en person (REC-1–5). */
@HiltViewModel(assistedFactory = RegimenEditViewModel.Factory::class)
class RegimenEditViewModel @AssistedInject constructor(
    @Assisted("personId") personId: String,
    @Assisted kind: RegimenKind,
    @Assisted("regimenId") private val regimenId: String?,
    regimens: RegimenRepository,
    households: HouseholdRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(@Assisted("personId") personId: String, kind: RegimenKind, @Assisted("regimenId") regimenId: String?): RegimenEditViewModel
    }

    private val collection = regimens.forPerson(personId)

    val isNew: Boolean = regimenId == null

    // Ett nytt recept börjar som en tablett om dagen, så att förhandsvisningen direkt visar något.
    val editor: EditorState<Regimen> = EditorState(
        Regimen(
            id = regimenId ?: collection.newId(),
            kind = kind,
            form = MedicineForm.TABLET,
            unit = ItemUnit.TABLETT,
            schedule = DoseSchedule.Daily(),
        ),
        RegimenValidator,
        loading = !isNew,
    )
    val state = editor.state
    val effects: Flow<EditorEffect> = editor.effects

    /** Det lagrade namnet ("Levaxin 50 µg") – det som raderas, oavsett vad som skrivits i fältet. */
    private val _storedName = MutableStateFlow("")
    val storedName: StateFlow<String> = _storedName.asStateFlow()

    /** Vad som skulle packas för en 7-dagarsresa med hushållets marginal (REC-4). */
    val preview: StateFlow<PackingPreviewText> = combine(
        editor.state.map { it.value }.distinctUntilChanged(),
        households.current.map { it?.settings ?: HouseholdSettings() }.catch { emit(HouseholdSettings()) },
    ) { regimen, settings -> PackingEngine.regimenPreview(regimen, settings, clock.todayIn(timeZone)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PackingEngine.regimenPreview(editor.value, HouseholdSettings(), clock.todayIn(timeZone)))

    init {
        load()
    }

    fun onEvent(event: RegimenEditEvent) {
        when (event) {
            is RegimenEditEvent.Name -> editor.update(RegimenValidator.NAME) { it.copy(name = event.name) }
            is RegimenEditEvent.Strength -> editor.update { it.copy(strength = event.strength) }
            is RegimenEditEvent.Form -> editor.update { it.copy(form = event.form) }
            is RegimenEditEvent.DoseUnit -> editor.update { it.copy(unit = event.unit) }
            is RegimenEditEvent.Schedule -> {
                // Ett byte av typ visar inget fel än (t.ex. Veckodagar utan valda dagar) – först när
                // typens fält ändras, eller vid sparförsök.
                val sameKind = editor.value.schedule.kindLabel() == event.schedule.kindLabel()
                val touched = if (sameKind) arrayOf(RegimenValidator.SCHEDULE) else emptyArray()
                editor.update(*touched) { it.copy(schedule = event.schedule) }
            }
            is RegimenEditEvent.PackageSize -> editor.update(RegimenValidator.PACKAGE_SIZE) {
                it.copy(packageSize = event.text.filter(Char::isDigit).take(MAX_PACKAGE_DIGITS).toIntOrNull())
            }
            is RegimenEditEvent.Notes -> editor.update { it.copy(notes = event.notes) }
            is RegimenEditEvent.Active -> editor.update { it.copy(active = event.active) }
            RegimenEditEvent.Save -> viewModelScope.launch { editor.save(::write) }
            RegimenEditEvent.Delete -> viewModelScope.launch { editor.run { collection.delete(editor.value.id) } }
            RegimenEditEvent.Retry -> {
                editor.reload()
                load()
            }
        }
    }

    private fun load() {
        val id = regimenId ?: return
        viewModelScope.launch { editor.loadFrom({ collection.get(id) }) { it.also { stored -> _storedName.value = stored.displayName } } }
    }

    /** Texterna sparas utan omgivande blanksteg; ett nytt recept hamnar sist. */
    private suspend fun write(regimen: Regimen): Result<Unit> {
        val trimmed = regimen.copy(name = regimen.name.trim(), strength = regimen.strength.trim(), notes = regimen.notes.trim())
        return collection.upsert(if (isNew) trimmed.copy(sortOrder = collection.nextSortOrder()) else trimmed)
    }

    private companion object {
        /** Upp till 99 999 per förpackning – samma tak som en mängd (`PackingEngine.MAX_QUANTITY`). */
        const val MAX_PACKAGE_DIGITS = 5
    }
}

@Composable
fun RegimenEditScreen(config: RegimenEditorConfig, onClose: () -> Unit, viewModel: RegimenEditViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val storedName by viewModel.storedName.collectAsStateWithLifecycle()
    RegimenEditContent(config, viewModel.isNew, state, preview, viewModel.effects, viewModel::onEvent, onClose, storedName = storedName)
}

/**
 * Formuläret i mockupens ordning (variant B: enheten väljs för sig, oberoende av formen).
 * Rubriken är fast ("Nytt recept"/"Redigera recept") som i alla formulär; [storedName] är det
 * lagrade namnet som radering frågar om.
 */
@Composable
fun RegimenEditContent(
    config: RegimenEditorConfig,
    isNew: Boolean,
    state: EditorUiState<Regimen>,
    preview: PackingPreviewText,
    effects: Flow<EditorEffect>,
    onEvent: (RegimenEditEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    storedName: String = "",
) {
    val regimen = state.value
    EntityEditScreen(
        title = stringResource(if (isNew) config.newTitle else config.editTitle),
        state = state,
        effects = effects,
        onSave = { onEvent(RegimenEditEvent.Save) },
        onClose = onClose,
        modifier = modifier,
        delete = if (isNew) {
            null
        } else {
            DeleteAction(stringResource(R.string.delete_title_format, storedName), stringResource(config.deleteMessage)) { onEvent(RegimenEditEvent.Delete) }
        },
        onRetry = { onEvent(RegimenEditEvent.Retry) },
    ) {
        AppTextField(
            regimen.name,
            { onEvent(RegimenEditEvent.Name(it)) },
            stringResource(R.string.name),
            error = state.errorFor(RegimenValidator.NAME)?.let { stringResource(it) },
        )
        AppTextField(
            regimen.strength,
            { onEvent(RegimenEditEvent.Strength(it)) },
            stringResource(R.string.regimen_strength),
            helper = stringResource(R.string.regimen_strength_helper),
        )
        LabeledGroup(stringResource(R.string.regimen_form)) {
            ChipRow {
                config.forms.forEach { form ->
                    AppFilterChip(stringResource(form.label()), selected = regimen.form == form, onClick = { onEvent(RegimenEditEvent.Form(form)) })
                }
            }
        }
        LabeledGroup(stringResource(R.string.regimen_unit)) {
            ChipRow {
                ItemUnit.entries.forEach { unit ->
                    AppFilterChip(UnitNames.name(1, unit), selected = regimen.unit == unit, onClick = { onEvent(RegimenEditEvent.DoseUnit(unit)) })
                }
            }
        }
        LabeledGroup(stringResource(R.string.regimen_schedule)) {
            DoseScheduleRow(
                regimen.schedule,
                { onEvent(RegimenEditEvent.Schedule(it)) },
                unitName = { UnitNames.name(it, regimen.unit) },
                error = state.errorFor(RegimenValidator.SCHEDULE)?.let { stringResource(it) },
            )
        }
        PackingPreview(preview.context, preview.quantity, reason = preview.reason)
        AppTextField(
            regimen.packageSize?.toString().orEmpty(),
            { onEvent(RegimenEditEvent.PackageSize(it)) },
            stringResource(R.string.regimen_package_size),
            error = state.errorFor(RegimenValidator.PACKAGE_SIZE)?.let { stringResource(it) },
            helper = stringResource(R.string.regimen_package_size_helper),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        AppTextField(regimen.notes, { onEvent(RegimenEditEvent.Notes(it)) }, stringResource(R.string.regimen_notes), singleLine = false)
        SwitchRow(
            stringResource(R.string.regimen_active),
            regimen.active,
            { onEvent(RegimenEditEvent.Active(it)) },
            subtitle = stringResource(R.string.regimen_active_note),
        )
    }
}
