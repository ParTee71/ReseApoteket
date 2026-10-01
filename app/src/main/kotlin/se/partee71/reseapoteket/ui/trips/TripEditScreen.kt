package se.partee71.reseapoteket.ui.trips

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.engine.PackingPreviewText
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.Trip
import se.partee71.reseapoteket.data.repository.PersonRepository
import se.partee71.reseapoteket.data.repository.TripRepository
import se.partee71.reseapoteket.domain.TripPacking
import se.partee71.reseapoteket.ui.common.DateFormat
import se.partee71.reseapoteket.ui.common.EditorEffect
import se.partee71.reseapoteket.ui.common.EditorLoader
import se.partee71.reseapoteket.ui.common.EditorState
import se.partee71.reseapoteket.ui.common.EditorUiState
import se.partee71.reseapoteket.ui.common.Validator
import se.partee71.reseapoteket.ui.components.AppTextField
import se.partee71.reseapoteket.ui.components.ChipRow
import se.partee71.reseapoteket.ui.components.ConfirmDialog
import se.partee71.reseapoteket.ui.components.DateField
import se.partee71.reseapoteket.ui.components.EntityEditScreen
import se.partee71.reseapoteket.ui.components.LabeledGroup
import se.partee71.reseapoteket.ui.components.PackingPreview
import se.partee71.reseapoteket.ui.components.PersonChip
import se.partee71.reseapoteket.ui.components.QuantityStepper
import se.partee71.reseapoteket.ui.theme.AppColors

/** RES-2: 1–60 dagar och minst en som åker med; ett tomt namn blir "Resa <datum>" när det finns ett startdatum. */
object TripValidator : Validator<Trip> {
    const val NAME = "name"
    const val DAYS = "days"
    const val TRAVELLERS = "travellers"
    val DAY_RANGE = 1..60

    override fun validate(value: Trip): Map<String, Int> = buildMap {
        if (value.name.isBlank() && value.startDate == null) put(NAME, R.string.name_required)
        if (value.days !in DAY_RANGE) put(DAYS, R.string.trip_days_range)
        if (value.travellerIds.isEmpty()) put(TRAVELLERS, R.string.trip_travellers_required)
    }
}

sealed interface TripEditEvent {
    data class Name(val name: String) : TripEditEvent

    data class Start(val date: LocalDate) : TripEditEvent

    data class Days(val days: Int) : TripEditEvent

    data class Traveller(val personId: String) : TripEditEvent

    /** Spara; [defaultName] ("Resa 2 okt") används om namnet är tomt. */
    data class Save(val defaultName: String) : TripEditEvent

    /** Svaret på "Räkna om packlistan?" (RES-4): spara, och räkna om eller behåll listan. */
    data class Recount(val recount: Boolean) : TripEditEvent

    data object CancelRecount : TripEditEvent

    data object Archive : TripEditEvent

    data object Restore : TripEditEvent

    data object Retry : TripEditEvent
}

/**
 * Skapa ([tripId] = `null`) eller ändra en resa (RES-2–4). En ny resa börjar i morgon, 7 dagar,
 * med alla som inte är arkiverade och namnet "Resa <datum>"; "Skapa packlista" sparar den och
 * skriver packlistan. Ändras dagar, startdatum eller resenärer frågar Spara om listan ska räknas om.
 */
@HiltViewModel(assistedFactory = TripEditViewModel.Factory::class)
class TripEditViewModel @AssistedInject constructor(
    @Assisted private val tripId: String?,
    private val trips: TripRepository,
    persons: PersonRepository,
    private val packing: TripPacking,
    private val clock: Clock,
    timeZone: TimeZone,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(tripId: String?): TripEditViewModel
    }

    val isNew: Boolean = tripId == null

    val editor: EditorState<Trip> = EditorState(Trip(tripId ?: trips.newId(), days = DEFAULT_DAYS), TripValidator, loading = !isNew)
    val state = editor.state
    val effects: Flow<EditorEffect> = editor.effects
    private val loader = EditorLoader(editor, viewModelScope, tripId?.let { id -> { trips.get(id) } })

    /** Den lagrade resan – vad som räknas om jämförs mot, och vad som arkiveras eller återställs. */
    val stored: StateFlow<Trip?> = loader.stored

    /** Hushållets personer som kan åka med; arkiverade bara om de redan är resenärer. */
    val people: StateFlow<List<Person>> = persons.observe()
        .combine(editor.state.map { it.value.travellerIds }.distinctUntilChanged()) { all, ids -> all.filter { !it.archived || it.id in ids } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Vad packlistan skulle bli (RES-3); `null` tills underlaget är läst, eller om det inte går. */
    val preview: StateFlow<PackingPreviewText?> = packing.preview(editor.state.map { it.value })
        .map<PackingPreviewText, PackingPreviewText?> { it }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var defaultName = ""

    private val _askRecount = MutableStateFlow(false)
    val askRecount: StateFlow<Boolean> = _askRecount.asStateFlow()

    init {
        if (isNew) {
            // Förvalen är en ändring av ett tomt formulär: "Skapa packlista" är aktiv direkt.
            val start = clock.todayIn(timeZone).plus(DatePeriod(days = 1))
            editor.update { it.copy(name = "", startDate = start) }
            viewModelScope.launch {
                // Går personerna inte att läsa väljer användaren själv; felet syns redan i listan.
                val everyone = persons.observe().catch { }.firstOrNull().orEmpty().filterNot { it.archived }.map { it.id }
                // Har användaren redan valt någon skrivs valet inte över.
                if (everyone.isNotEmpty()) editor.update { if (it.travellerIds.isEmpty()) it.copy(travellerIds = everyone) else it }
            }
        }
    }

    fun onEvent(event: TripEditEvent) {
        when (event) {
            is TripEditEvent.Name -> editor.update(TripValidator.NAME) { it.copy(name = event.name) }
            is TripEditEvent.Start -> editor.update { it.copy(startDate = event.date) }
            is TripEditEvent.Days -> editor.update(TripValidator.DAYS) { it.copy(days = event.days) }
            is TripEditEvent.Traveller -> editor.update(TripValidator.TRAVELLERS) {
                val ids = it.travellerIds
                it.copy(travellerIds = if (event.personId in ids) ids - event.personId else ids + event.personId)
            }
            is TripEditEvent.Save -> {
                defaultName = event.defaultName
                if (!isNew && changesPacking()) _askRecount.value = true else save(recount = false)
            }
            is TripEditEvent.Recount -> {
                _askRecount.value = false
                save(event.recount)
            }
            TripEditEvent.CancelRecount -> _askRecount.value = false
            TripEditEvent.Archive -> setArchived(true)
            TripEditEvent.Restore -> setArchived(false)
            TripEditEvent.Retry -> loader.retry()
        }
    }

    /** Dagar, startdatum och resenärer är det packlistan räknas på (RES-4). */
    private fun changesPacking(): Boolean {
        val before = stored.value ?: return false
        val now = editor.value
        return before.days != now.days || before.startDate != now.startDate || before.travellerIds.toSet() != now.travellerIds.toSet()
    }

    private fun save(recount: Boolean) {
        viewModelScope.launch { editor.save { trip -> write(trip, recount) } }
    }

    private fun setArchived(archived: Boolean) {
        viewModelScope.launch { editor.run { trips.setArchived(editor.value.id, archived) } }
    }

    /**
     * Ett tomt namn blir "Resa <datum>". En ny resa får sin packlista först, så att den aldrig
     * finns utan lista; en ändrad räknas om på begäran.
     */
    private suspend fun write(trip: Trip, recount: Boolean): Result<Unit> {
        val now = clock.now()
        val named = trip.copy(name = trip.name.trim().ifEmpty { defaultName }, updatedAt = now, createdAt = trip.createdAt ?: now)
        if (isNew) packing.create(named).onFailure { return Result.failure(it) }
        val saved = trips.upsert(named)
        return if (saved.isSuccess && recount) packing.recount(named) else saved
    }

    private companion object {
        const val DEFAULT_DAYS = 7
    }
}

@Composable
fun TripEditScreen(onClose: () -> Unit, onCreated: (String) -> Unit, viewModel: TripEditViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val stored by viewModel.stored.collectAsStateWithLifecycle()
    val askRecount by viewModel.askRecount.collectAsStateWithLifecycle()
    TripEditContent(
        isNew = viewModel.isNew,
        state = state,
        people = people,
        preview = preview,
        effects = viewModel.effects,
        onEvent = viewModel::onEvent,
        onClose = onClose,
        onSaved = if (viewModel.isNew) ({ onCreated(state.value.id) }) else onClose,
        stored = stored,
        askRecount = askRecount,
    )
}

/** Formuläret i mockupens ordning: vart, startdatum, dagar, vem åker med och vad som packas. */
@Composable
fun TripEditContent(
    isNew: Boolean,
    state: EditorUiState<Trip>,
    people: List<Person>,
    preview: PackingPreviewText?,
    effects: Flow<EditorEffect>,
    onEvent: (TripEditEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onSaved: () -> Unit = onClose,
    stored: Trip? = null,
    askRecount: Boolean = false,
) {
    val trip = state.value
    val defaultName = trip.startDate?.let { stringResource(R.string.trip_default_name, DateFormat.short(it)) }.orEmpty()
    EntityEditScreen(
        title = stringResource(if (isNew) R.string.trips_add else R.string.trip_edit),
        state = state,
        effects = effects,
        onSave = { onEvent(TripEditEvent.Save(defaultName)) },
        onClose = onClose,
        modifier = modifier,
        saveLabel = stringResource(if (isNew) R.string.trip_create else R.string.save),
        onArchive = stored?.takeUnless { it.archived }?.let { { onEvent(TripEditEvent.Archive) } },
        onRestore = stored?.takeIf { it.archived }?.let { { onEvent(TripEditEvent.Restore) } },
        onRetry = { onEvent(TripEditEvent.Retry) },
        onSaved = onSaved,
    ) {
        AppTextField(
            trip.name,
            { onEvent(TripEditEvent.Name(it)) },
            stringResource(R.string.trip_where),
            error = state.errorFor(TripValidator.NAME)?.let { stringResource(it) },
            helper = if (trip.name.isBlank() && defaultName.isNotEmpty()) defaultName else stringResource(R.string.trip_where_helper),
        )
        DateField(stringResource(R.string.trip_start), trip.startDate, { onEvent(TripEditEvent.Start(it)) })
        LabeledGroup(stringResource(R.string.trip_days_label)) {
            QuantityStepper(trip.days, { onEvent(TripEditEvent.Days(it)) }, stringResource(R.string.trip_days_unit), range = TripValidator.DAY_RANGE)
        }
        LabeledGroup(stringResource(R.string.trip_travellers), error = state.errorFor(TripValidator.TRAVELLERS)?.let { stringResource(it) }) {
            ChipRow {
                people.forEach { person ->
                    PersonChip(person.name, person.emoji, AppColors.person(person.colorHex), person.id in trip.travellerIds, { onEvent(TripEditEvent.Traveller(person.id)) })
                }
            }
        }
        preview?.let { PackingPreview(it.context, it.quantity, reason = it.reason) }
    }
    if (askRecount) {
        ConfirmDialog(
            title = stringResource(R.string.trip_recount_title),
            message = stringResource(R.string.trip_recount_message),
            confirmLabel = stringResource(R.string.trip_recount_confirm),
            onConfirm = { onEvent(TripEditEvent.Recount(true)) },
            onDismiss = { onEvent(TripEditEvent.CancelRecount) },
            dismissLabel = stringResource(R.string.trip_recount_keep),
            onDecline = { onEvent(TripEditEvent.Recount(false)) },
        )
    }
}
