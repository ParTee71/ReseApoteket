package se.partee71.reseapoteket.ui.packing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.time.Clock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.engine.DoseScheduleFormatter
import se.partee71.reseapoteket.core.engine.PackingEngine
import se.partee71.reseapoteket.core.engine.PackingList
import se.partee71.reseapoteket.core.engine.PackingProgress
import se.partee71.reseapoteket.core.model.ItemUnit
import se.partee71.reseapoteket.core.model.PackingItem
import se.partee71.reseapoteket.core.model.PackingSection
import se.partee71.reseapoteket.core.model.PackingSource
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.Regimen
import se.partee71.reseapoteket.core.model.Trip
import se.partee71.reseapoteket.core.model.TripStatus
import se.partee71.reseapoteket.core.schema.PackingItemCodec
import se.partee71.reseapoteket.core.schema.TripCodec
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.combineByKey
import se.partee71.reseapoteket.data.common.dataError
import se.partee71.reseapoteket.data.common.upsertPlaced
import se.partee71.reseapoteket.data.common.withFallback
import se.partee71.reseapoteket.data.datastore.SettingsStore
import se.partee71.reseapoteket.data.repository.PackingRepository
import se.partee71.reseapoteket.data.repository.PersonRepository
import se.partee71.reseapoteket.data.repository.RegimenRepository
import se.partee71.reseapoteket.data.repository.TripRepository
import se.partee71.reseapoteket.domain.TripPacking
import se.partee71.reseapoteket.ui.common.ArchiveActions
import se.partee71.reseapoteket.ui.common.EditorEffect
import se.partee71.reseapoteket.ui.common.EditorState
import se.partee71.reseapoteket.ui.common.ListEvent
import se.partee71.reseapoteket.ui.common.ListLoader
import se.partee71.reseapoteket.ui.common.ListUiState
import se.partee71.reseapoteket.ui.common.Validator

/** En rad i packlistan: raden, personen den hör till och doseringen i kort form ("1/dag", PAK-2). */
data class PackingRow(val item: PackingItem, val person: Person? = null, val schedule: String? = null)

/** Rubrikområdet: resan, resenärerna och packat, totalt och per sektion (PAK-1). */
data class PackingHeader(
    val trip: Trip,
    val travellers: List<Person>,
    val progress: PackingProgress,
    val sections: Map<PackingSection, PackingProgress>,
)

/** En egen rad (PAK-4): namn krävs, och en person för mediciner och kosttillskott. */
object ManualItemValidator : Validator<PackingItem> {
    const val NAME = "name"
    const val PERSON = "person"
    val QUANTITY_RANGE = 1..999

    override fun validate(value: PackingItem): Map<String, Int> = buildMap {
        if (value.name.isBlank()) put(NAME, R.string.name_required)
        if (value.section != PackingSection.FAMILY && value.personId == null) put(PERSON, R.string.packing_person_required)
    }
}

sealed interface PackingListEvent {
    data class List(val event: ListEvent) : PackingListEvent

    /** Pricka av eller ta bort avprickningen (PAK-2, PAK-5). */
    data class Check(val id: String, val checked: Boolean) : PackingListEvent

    /** Öppna mängden för en rad (PAK-3). */
    data class Open(val id: String) : PackingListEvent

    data object CloseQuantity : PackingListEvent

    data class Quantity(val quantity: Int) : PackingListEvent

    data object ResetQuantity : PackingListEvent

    /** "Visa igen" för en dold rad (PAK-4). */
    data class Show(val id: String) : PackingListEvent

    data object HideChecked : PackingListEvent

    data object AskRecount : PackingListEvent

    data object Recount : PackingListEvent

    data object CancelRecount : PackingListEvent

    /** "Lägg till" – en egen rad (PAK-4). */
    data object Add : PackingListEvent

    data object CloseAdd : PackingListEvent

    data class AddName(val name: String) : PackingListEvent

    data class AddSection(val section: PackingSection) : PackingListEvent

    data class AddPerson(val personId: String) : PackingListEvent

    data class AddQuantity(val quantity: Int) : PackingListEvent

    data object AddSave : PackingListEvent
}

/**
 * Packlistan för en resa (PAK-1–5): avprickning, mängd, egna rader, dölj och "Räkna om".
 * Varje ändring skriver bara de fält den ändrar. Efter en egen ändring följer resans status listan
 * som den står i cachen (packad när allt är packat; bara statusfältet skrivs), och blir listan
 * då helt packad firas det en gång med konfetti. Att bara öppna listan ändrar ingenting.
 */
@HiltViewModel(assistedFactory = PackingListViewModel.Factory::class)
class PackingListViewModel @AssistedInject constructor(
    @Assisted private val tripId: String,
    private val trips: TripRepository,
    persons: PersonRepository,
    regimens: RegimenRepository,
    packing: PackingRepository,
    settings: SettingsStore,
    private val tripPacking: TripPacking,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(tripId: String): PackingListViewModel
    }

    private val collection = packing.forTrip(tripId)

    /** Svep döljer en rad och kan ångras – samma mekanik som arkivering i listorna (NFR-3). */
    val hiding = ArchiveActions(viewModelScope, ::setHidden, R.string.hidden_format)

    val trip: StateFlow<Trip?> = trips.observe(tripId).withFallback(null).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val people: Flow<List<Person>> = persons.observe().withFallback(emptyList())

    // Raderna i listans ordning, dolda sist; doseringen hämtas per uppsättning personer.
    private val rows: Flow<List<PackingRow>> =
        combine(collection.observe(), people, settings.moveCheckedLast.withFallback(true), ::Basis)
            .combineByKey({ basis -> basis.items.mapNotNull { it.personId }.toSet() }, { ids -> regimens.regimensFor(ids.toList()).withFallback(emptyMap()) }) { basis, byPerson ->
                val arranged = PackingList.arrange(basis.items, basis.people, basis.checkedLast)
                val byId = basis.people.associateBy { it.id }
                (arranged.visible + arranged.hidden).map { item -> PackingRow(item, item.personId?.let(byId::get), schedule(item, byPerson)) }
            }

    private val loader = ListLoader(rows, viewModelScope)
    val state = loader.state

    val header: StateFlow<PackingHeader?> = combine(trip, state, people) { trip, list, all ->
        trip?.let {
            val items = currentRows(list).map { row -> row.item }
            val shown = items.filterNot { item -> item.hidden }
            PackingHeader(
                trip = it,
                travellers = it.travellerIds.mapNotNull { id -> all.firstOrNull { person -> person.id == id } },
                progress = PackingEngine.progress(items),
                sections = PackingSection.entries.associateWith { section -> PackingEngine.progress(shown.filter { item -> item.section == section }) },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val openId = MutableStateFlow<String?>(null)

    /** Raden vars mängd ändras (PAK-3), som den ser ut just nu. */
    val editing: StateFlow<PackingRow?> = combine(openId, state) { id, list ->
        id?.let { wanted -> currentRows(list).firstOrNull { it.item.id == wanted } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _adding = MutableStateFlow(false)
    val adding: StateFlow<Boolean> = _adding.asStateFlow()

    /** Den egna raden som läggs till (PAK-4). */
    val addEditor: EditorState<PackingItem> = EditorState(newManualItem(), ManualItemValidator)

    private val _askRecount = MutableStateFlow(false)
    val askRecount: StateFlow<Boolean> = _askRecount.asStateFlow()

    private val _celebrations = Channel<Unit>(Channel.CONFLATED)

    /** En händelse när listan just blev helt packad (PAK-5) – konfetti en gång, inte vid nästa visning. */
    val celebrations: Flow<Unit> = _celebrations.receiveAsFlow()

    private val _sheetError = MutableStateFlow<DataError?>(null)

    /** Ett skrivfel medan en panel är öppen – visas i panelen, som ligger över listans meddelanden. */
    val sheetError: StateFlow<DataError?> = _sheetError.asStateFlow()

    /** Användaren har ändrat något sedan listan öppnades – först då följer statusen listan (PAK-5). */
    private var changedHere = false

    /** Resans status som den senast lästs eller skrivits härifrån. */
    private var knownStatus: TripStatus? = null

    init {
        viewModelScope.launch { trip.filterNotNull().collect { knownStatus = it.status } }
        // Efter en egen ändring följer resans status packat-läget – bara när det ändras, och mot
        // statusen som senast skrevs härifrån, så att snabba ändringar i följd hamnar rätt innan
        // resan läst tillbaka sin status. Det firas när listan just blev helt packad. Ett
        // misslyckat försök görs om vid nästa ändring.
        viewModelScope.launch {
            var packedBefore: Boolean? = null
            header.mapNotNull { it?.progress }.distinctUntilChanged().collect { packed ->
                val justPacked = packedBefore == false && packed.isComplete
                if (packed.total > 0) packedBefore = packed.isComplete
                val current = knownStatus ?: return@collect
                val wanted = PackingList.statusAfter(current, packed)
                if (!changedHere || wanted == current) return@collect
                knownStatus = wanted
                if (justPacked && wanted == TripStatus.PACKED) _celebrations.trySend(Unit)
                // Bara statusfältet skrivs; ett fel visas i listan, panelen kan redan vara stängd.
                trips.update(Trip(tripId, status = wanted), STATUS_FIELDS).onFailure { knownStatus = current }.dataError()?.let(hiding::report)
            }
        }

        // Lyckad sparning av en egen rad stänger panelen och börjar om med en tom rad.
        viewModelScope.launch {
            addEditor.effects.collect { effect ->
                when (effect) {
                    EditorEffect.Done -> {
                        _adding.value = false
                        addEditor.load(newManualItem())
                    }
                    is EditorEffect.Failed -> report(effect.error)
                }
            }
        }
    }

    fun onEvent(event: PackingListEvent) {
        when (event) {
            is PackingListEvent.List -> loader.onEvent(event.event)
            is PackingListEvent.Check -> check(event.id, event.checked)
            is PackingListEvent.Open -> openSheet { openId.value = event.id }
            PackingListEvent.CloseQuantity -> openSheet { openId.value = null }
            is PackingListEvent.Quantity -> editItem { it.copy(quantity = event.quantity) }
            PackingListEvent.ResetQuantity -> editItem { it.copy(quantity = it.suggestedQuantity) }
            is PackingListEvent.Show -> write { setHidden(event.id, false) }
            PackingListEvent.HideChecked -> hideChecked()
            PackingListEvent.AskRecount -> _askRecount.value = true
            PackingListEvent.CancelRecount -> _askRecount.value = false
            PackingListEvent.Recount -> recount()
            PackingListEvent.Add -> openSheet { _adding.value = true }
            PackingListEvent.CloseAdd -> openSheet {
                _adding.value = false
                addEditor.load(newManualItem())
            }
            // Ett gammalt fel står inte kvar medan raden ändras.
            is PackingListEvent.AddName -> editNew(ManualItemValidator.NAME) { it.copy(name = event.name) }
            is PackingListEvent.AddSection -> editNew {
                it.copy(section = event.section, personId = it.personId.takeIf { event.section != PackingSection.FAMILY })
            }
            is PackingListEvent.AddPerson -> editNew(ManualItemValidator.PERSON) { it.copy(personId = event.personId) }
            is PackingListEvent.AddQuantity -> editNew { it.copy(quantity = event.quantity, suggestedQuantity = event.quantity) }
            PackingListEvent.AddSave -> addSave()
        }
    }

    /** Öppnar eller stänger en panel; ett fel från förra panelen följer inte med. */
    private fun openSheet(change: () -> Unit) {
        _sheetError.value = null
        change()
    }

    private fun editNew(vararg fields: String, change: (PackingItem) -> PackingItem) {
        _sheetError.value = null
        addEditor.update(*fields, transform = change)
    }

    private fun addSave() {
        _sheetError.value = null
        changedHere = true
        viewModelScope.launch {
            addEditor.save { item -> collection.upsertPlaced(item.copy(name = item.name.trim()), isNew = true) { copy(sortOrder = it) } }
        }
    }

    /** Pricka av med tidpunkt (PAK-2). */
    private fun check(id: String, checked: Boolean) {
        val item = currentItems().firstOrNull { it.id == id } ?: return
        write { collection.update(item.copy(checked = checked, checkedAt = if (checked) clock.now() else null), CHECK_FIELDS) }
    }

    private fun editItem(change: (PackingItem) -> PackingItem) {
        val item = editing.value?.item ?: return
        _sheetError.value = null
        write { collection.update(change(item), QUANTITY_FIELDS) }
    }

    private fun hideChecked() {
        val checked = currentItems().filter { it.checked && !it.hidden }
        if (checked.isEmpty()) return
        write { collection.updateAll(checked.map { it.copy(hidden = true) }, HIDDEN_FIELDS) }
    }

    private fun recount() {
        _askRecount.value = false
        write {
            val trip = trip.value ?: trips.get(tripId).getOrElse { return@write Result.failure(it) } ?: return@write Result.failure(DataError.NotFound)
            tripPacking.recount(trip)
        }
    }

    private suspend fun setHidden(id: String, hidden: Boolean): Result<Unit> {
        changedHere = true
        val item = currentItems().firstOrNull { it.id == id } ?: return Result.success(Unit)
        return collection.update(item.copy(hidden = hidden), HIDDEN_FIELDS)
    }

    private fun currentItems(): List<PackingItem> = currentRows(state.value).map { it.item }

    /** En egen skrivning; ett fel visas i den öppna panelen, annars som meddelande i listan. */
    private fun write(block: suspend () -> Result<Unit>) {
        changedHere = true
        viewModelScope.launch { block().dataError()?.let(::report) }
    }

    private fun report(error: DataError) {
        if (editing.value != null || _adding.value) _sheetError.value = error else hiding.report(error)
    }

    private fun newManualItem() = PackingItem(
        id = collection.newId(),
        section = PackingSection.FAMILY,
        sourceType = PackingSource.MANUAL,
        unit = ItemUnit.ST,
        suggestedQuantity = 1,
        quantity = 1,
    )

    private class Basis(val items: List<PackingItem>, val people: List<Person>, val checkedLast: Boolean)

    private companion object {
        val STATUS_FIELDS = setOf(TripCodec.STATUS)
        val CHECK_FIELDS = setOf(PackingItemCodec.CHECKED, PackingItemCodec.CHECKED_AT)
        val QUANTITY_FIELDS = setOf(PackingItemCodec.QUANTITY)
        val HIDDEN_FIELDS = setOf(PackingItemCodec.HIDDEN)

        fun currentRows(state: ListUiState<PackingRow>): List<PackingRow> = (state as? ListUiState.Content)?.items.orEmpty()

        /** "1/dag", "vid behov" för ett recept eller kosttillskott; inget för familjeartiklar och egna rader. */
        fun schedule(item: PackingItem, byPerson: Map<String, List<Regimen>>): String? {
            if (item.sourceType != PackingSource.REGIMEN) return null
            val regimen = byPerson[item.personId].orEmpty().firstOrNull { it.id == item.sourceId } ?: return null
            return DoseScheduleFormatter.short(regimen.schedule)
        }
    }
}
