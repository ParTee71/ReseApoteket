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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.engine.PackingEngine
import se.partee71.reseapoteket.core.engine.PackingPreviewText
import se.partee71.reseapoteket.core.engine.UnitNames
import se.partee71.reseapoteket.core.medicine.MedicineEntry
import se.partee71.reseapoteket.core.medicine.MedicineLookup
import se.partee71.reseapoteket.core.medicine.fillFrom
import se.partee71.reseapoteket.core.medicine.lookup
import se.partee71.reseapoteket.core.model.DoseSchedule
import se.partee71.reseapoteket.core.model.HouseholdSettings
import se.partee71.reseapoteket.core.model.ItemUnit
import se.partee71.reseapoteket.core.model.MedicineForm
import se.partee71.reseapoteket.core.model.Regimen
import se.partee71.reseapoteket.core.model.RegimenKind
import se.partee71.reseapoteket.core.model.displayName
import se.partee71.reseapoteket.data.common.upsertPlaced
import se.partee71.reseapoteket.data.medicines.MedicineRepository
import se.partee71.reseapoteket.data.repository.HouseholdRepository
import se.partee71.reseapoteket.data.repository.RegimenRepository
import se.partee71.reseapoteket.data.repository.householdSettings
import se.partee71.reseapoteket.ui.common.EditorEffect
import se.partee71.reseapoteket.ui.common.EditorLoader
import se.partee71.reseapoteket.ui.common.EditorState
import se.partee71.reseapoteket.ui.common.EditorUiState
import se.partee71.reseapoteket.ui.common.TravelDayChange
import se.partee71.reseapoteket.ui.common.Validator
import se.partee71.reseapoteket.ui.components.AppTextField
import se.partee71.reseapoteket.ui.components.ChoiceChips
import se.partee71.reseapoteket.ui.components.DeleteAction
import se.partee71.reseapoteket.ui.components.DoseScheduleRow
import se.partee71.reseapoteket.ui.components.EntityEditScreen
import se.partee71.reseapoteket.ui.components.ExampleChips
import se.partee71.reseapoteket.ui.components.InfoPill
import se.partee71.reseapoteket.ui.components.LabeledGroup
import se.partee71.reseapoteket.ui.components.PackingPreview
import se.partee71.reseapoteket.ui.components.Suggestion
import se.partee71.reseapoteket.ui.components.SuggestionField
import se.partee71.reseapoteket.ui.components.SwitchRow
import se.partee71.reseapoteket.ui.components.TravelDayField
import se.partee71.reseapoteket.ui.components.kindLabel
import se.partee71.reseapoteket.ui.theme.Tone

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

    /** Packas på resedagen och ev. egen tid (PAK-6). */
    data class TravelDay(val change: TravelDayChange) : RegimenEditEvent

    /** Ett förslag ur Läkemedelsverkets lista valt under namnfältet (REC-7). */
    data class PickMedicine(val entry: MedicineEntry) : RegimenEditEvent

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
    medicines: MedicineRepository,
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

    private val loader = EditorLoader(editor, viewModelScope, regimenId?.let { id -> { collection.get(id) } })

    /** Det lagrade receptet – det som raderas och vars form alltid kan väljas, oavsett vad som ändrats i fälten. */
    val stored: StateFlow<Regimen?> = loader.stored

    /** Vad som skulle packas för en 7-dagarsresa med hushållets marginal (REC-4). */
    val preview: StateFlow<PackingPreviewText> = combine(
        editor.state.map { it.value }.distinctUntilChanged(),
        households.householdSettings(),
    ) { regimen, settings -> PackingEngine.regimenPreview(regimen, settings, clock.todayIn(timeZone)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PackingEngine.regimenPreview(editor.value, HouseholdSettings(), clock.todayIn(timeZone)))

    private val picked = MutableStateFlow<MedicineEntry?>(null)

    /**
     * Förslagen ur Läkemedelsverkets lista under namnfältet (REC-6, REC-7) – bara för något nytt av
     * en sort med uppslag ([RegimenEditorConfig.medicineLookup], samma flagga som formuläret läser).
     */
    val lookup: StateFlow<MedicineLookup> = if (!isNew || !RegimenKindConfig.of(kind).editor.medicineLookup) {
        MutableStateFlow(MedicineLookup())
    } else {
        combine(editor.state.map { it.value }.distinctUntilChanged(), picked, flow { emit(medicines.catalog()) }) { regimen, entry, catalog ->
            catalog.lookup(regimen, entry)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, MedicineLookup())
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
            is RegimenEditEvent.TravelDay -> editor.update { it.copy(travelDay = event.change.on, travelDayTime = event.change.ownTime) }
            is RegimenEditEvent.PickMedicine -> {
                picked.value = event.entry
                editor.update(RegimenValidator.NAME) { it.fillFrom(event.entry) }
            }
            RegimenEditEvent.Save -> viewModelScope.launch { editor.save(::write) }
            RegimenEditEvent.Delete -> viewModelScope.launch { editor.run { collection.delete(editor.value.id) } }
            RegimenEditEvent.Retry -> loader.retry()
        }
    }

    /** Texterna sparas utan omgivande blanksteg; ett nytt recept hamnar sist. */
    private suspend fun write(regimen: Regimen): Result<Unit> {
        val trimmed = regimen.copy(name = regimen.name.trim(), strength = regimen.strength.trim(), notes = regimen.notes.trim())
        return collection.upsertPlaced(trimmed, isNew) { copy(sortOrder = it) }
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
    val stored by viewModel.stored.collectAsStateWithLifecycle()
    val lookup by viewModel.lookup.collectAsStateWithLifecycle()
    RegimenEditContent(config, viewModel.isNew, state, preview, viewModel.effects, viewModel::onEvent, onClose, stored = stored, lookup = lookup)
}

/**
 * Formuläret i mockupens ordning (variant B: enheten väljs för sig, oberoende av formen).
 * Rubriken är fast ("Nytt recept"/"Redigera recept") som i alla formulär. [stored] är det lagrade
 * receptet: radering frågar om dess namn, och dess form finns kvar bland valen även när den inte
 * hör till sortens former (KOS-2). [lookup] är förslagen ur Läkemedelsverkets lista (REC-6).
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
    stored: Regimen? = null,
    lookup: MedicineLookup = MedicineLookup(),
) {
    val regimen = state.value
    EntityEditScreen(
        title = stringResource(if (isNew) config.newTitle else config.editTitle),
        state = state,
        effects = effects,
        onSave = { onEvent(RegimenEditEvent.Save) },
        onClose = onClose,
        modifier = modifier,
        // Först när det lagrade receptet är läst finns något att radera.
        delete = stored?.let {
            DeleteAction(stringResource(R.string.delete_title_format, it.displayName), stringResource(config.deleteMessage)) { onEvent(RegimenEditEvent.Delete) }
        },
        onRetry = { onEvent(RegimenEditEvent.Retry) },
    ) {
        if (isNew && config.suggestions.isNotEmpty()) {
            LabeledGroup(stringResource(R.string.regimen_suggestions), helper = stringResource(R.string.regimen_suggestions_note)) {
                val names = config.suggestions.map { stringResource(it) }
                ExampleChips(names, { onEvent(RegimenEditEvent.Name(it)) }, chosen = regimen.name.trim())
            }
        }
        val nameError = state.errorFor(RegimenValidator.NAME)?.let { stringResource(it) }
        if (isNew && config.medicineLookup) {
            SuggestionField(
                regimen.name,
                { onEvent(RegimenEditEvent.Name(it)) },
                stringResource(R.string.name),
                suggestions = lookup.suggestions.map { Suggestion(it.entry.title, it.entry.formText, it.start until it.start + it.length) },
                onPick = { onEvent(RegimenEditEvent.PickMedicine(lookup.suggestions[it].entry)) },
                footer = stringResource(R.string.medicine_lookup_source),
                error = nameError,
                helper = stringResource(R.string.medicine_lookup_no_match).takeIf { lookup.noMatch },
            )
            if (lookup.filled) InfoPill(stringResource(R.string.medicine_lookup_filled), tone = Tone.Blue, icon = R.drawable.ic_check)
        } else {
            AppTextField(regimen.name, { onEvent(RegimenEditEvent.Name(it)) }, stringResource(R.string.name), error = nameError)
        }
        AppTextField(
            regimen.strength,
            { onEvent(RegimenEditEvent.Strength(it)) },
            stringResource(config.strength),
            helper = stringResource(config.strengthHelper),
        )
        LabeledGroup(stringResource(R.string.regimen_form)) {
            // En lagrad form utanför sortens lista (t.ex. Flytande på ett kosttillskott) går att
            // välja igen; formerna står alltid i samma ordning som i resten av appen.
            val forms = (config.forms + listOfNotNull(stored?.form)).distinct().sortedBy { it.ordinal }
            ChoiceChips(forms, regimen.form, { onEvent(RegimenEditEvent.Form(it)) }, { stringResource(it.label()) })
        }
        LabeledGroup(stringResource(R.string.unit)) {
            ChoiceChips(ItemUnit.entries, regimen.unit, { onEvent(RegimenEditEvent.DoseUnit(it)) }, { UnitNames.name(1, it) })
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
        TravelDayField(regimen.travelDay, regimen.travelDayTime, { onEvent(RegimenEditEvent.TravelDay(it)) })
        AppTextField(regimen.notes, { onEvent(RegimenEditEvent.Notes(it)) }, stringResource(R.string.notes), singleLine = false)
        SwitchRow(
            stringResource(R.string.active),
            regimen.active,
            { onEvent(RegimenEditEvent.Active(it)) },
            subtitle = stringResource(R.string.active_note),
        )
    }
}
