package se.partee71.reseapoteket.ui.familyitems

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.engine.PackingEngine
import se.partee71.reseapoteket.core.engine.PackingPreviewText
import se.partee71.reseapoteket.core.engine.UnitNames
import se.partee71.reseapoteket.core.model.FamilyItem
import se.partee71.reseapoteket.core.model.FamilyItemCategory
import se.partee71.reseapoteket.core.model.ItemUnit
import se.partee71.reseapoteket.core.model.QuantityRule
import se.partee71.reseapoteket.data.common.upsertPlaced
import se.partee71.reseapoteket.data.repository.FamilyItemRepository
import se.partee71.reseapoteket.data.repository.HouseholdRepository
import se.partee71.reseapoteket.data.repository.PersonRepository
import se.partee71.reseapoteket.data.repository.activeCount
import se.partee71.reseapoteket.data.repository.householdSettings
import se.partee71.reseapoteket.ui.common.EditorEffect
import se.partee71.reseapoteket.ui.common.EditorLoader
import se.partee71.reseapoteket.ui.common.EditorState
import se.partee71.reseapoteket.ui.common.EditorUiState
import se.partee71.reseapoteket.ui.common.Validator
import se.partee71.reseapoteket.ui.components.AppTextField
import se.partee71.reseapoteket.ui.components.ChoiceChips
import se.partee71.reseapoteket.ui.components.EntityEditScreen
import se.partee71.reseapoteket.ui.components.LabeledGroup
import se.partee71.reseapoteket.ui.components.PackingPreview
import se.partee71.reseapoteket.ui.components.QuantityRuleRow
import se.partee71.reseapoteket.ui.components.SwitchRow

/** FAM-2, FAM-3: namnet är obligatoriskt och mängdregeln ska gå att räkna på. */
object FamilyItemValidator : Validator<FamilyItem> {
    const val NAME = "name"
    const val RULE = "rule"

    override fun validate(value: FamilyItem): Map<String, Int> = buildMap {
        if (value.name.isBlank()) put(NAME, R.string.name_required)
        if (!value.rule.isValid()) put(RULE, R.string.rule_min_one)
    }

    /** En okänd regel (från en nyare app) kan inte ändras här och bevaras som den är (BCK-5). */
    private fun QuantityRule.isValid(): Boolean = when (this) {
        is QuantityRule.Fixed -> quantity >= 1
        is QuantityRule.PerTraveller -> quantity >= 1
        is QuantityRule.PerDays -> quantity >= 1 && perDays >= 1
        is QuantityRule.Unknown -> true
    }
}

sealed interface FamilyItemEditEvent {
    data class Name(val name: String) : FamilyItemEditEvent

    data class Category(val category: FamilyItemCategory) : FamilyItemEditEvent

    data class UnitChoice(val unit: ItemUnit) : FamilyItemEditEvent

    data class Rule(val rule: QuantityRule) : FamilyItemEditEvent

    data class Notes(val notes: String) : FamilyItemEditEvent

    data class Active(val active: Boolean) : FamilyItemEditEvent

    data object Save : FamilyItemEditEvent

    data object Archive : FamilyItemEditEvent

    data object Restore : FamilyItemEditEvent

    data object Retry : FamilyItemEditEvent
}

/**
 * Skapa ([itemId] = `null`) eller redigera en familjeartikel (FAM-2–4). En ny kan börja från ett
 * [example] med namnet [exampleName] (FAM-4); annars som "1 st" i Övrigt.
 */
@HiltViewModel(assistedFactory = FamilyItemEditViewModel.Factory::class)
class FamilyItemEditViewModel @AssistedInject constructor(
    @Assisted("itemId") private val itemId: String?,
    @Assisted example: FamilyItemExample?,
    @Assisted("exampleName") exampleName: String,
    private val items: FamilyItemRepository,
    persons: PersonRepository,
    households: HouseholdRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(@Assisted("itemId") itemId: String?, example: FamilyItemExample?, @Assisted("exampleName") exampleName: String): FamilyItemEditViewModel
    }

    val isNew: Boolean = itemId == null

    val editor: EditorState<FamilyItem> =
        EditorState(FamilyItem(itemId ?: items.newId(), rule = QuantityRule.Fixed()), FamilyItemValidator, loading = !isNew)
    val state = editor.state
    val effects: Flow<EditorEffect> = editor.effects
    private val loader = EditorLoader(editor, viewModelScope, itemId?.let { id -> { items.get(id) } })

    /** Den lagrade artikeln – arkiverad visar menyn "Återställ" i stället för "Arkivera"; `null` medan den läses. */
    val stored: StateFlow<FamilyItem?> = loader.stored

    /**
     * Vad som skulle packas för hushållets personer i 7 dagar med hushållets marginal (FAM-3);
     * `null` så länge antalet personer inte är känt – aldrig en mängd för noll resenärer som inte stämmer.
     */
    val preview: StateFlow<PackingPreviewText?> = combine(
        editor.state.map { it.value }.distinctUntilChanged(),
        persons.activeCount().map<Int, Int?> { it }.catch { emit(null) },
        households.householdSettings(),
    ) { item, travellers, settings -> travellers?.let { PackingEngine.familyPreview(item, it, settings) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Ett exempel är en ändring av ett tomt formulär: "Spara" är aktiv direkt, och bakåt frågar
        // "Släng ändringar?" – inget sparas förrän användaren sparar (FAM-4).
        example?.let { editor.update(FamilyItemValidator.NAME) { blank -> it.item(blank.id, exampleName) } }
    }

    fun onEvent(event: FamilyItemEditEvent) {
        when (event) {
            is FamilyItemEditEvent.Name -> editor.update(FamilyItemValidator.NAME) { it.copy(name = event.name) }
            is FamilyItemEditEvent.Category -> editor.update { it.copy(category = event.category) }
            is FamilyItemEditEvent.UnitChoice -> editor.update { it.copy(unit = event.unit) }
            is FamilyItemEditEvent.Rule -> editor.update(FamilyItemValidator.RULE) { it.copy(rule = event.rule) }
            is FamilyItemEditEvent.Notes -> editor.update { it.copy(notes = event.notes) }
            is FamilyItemEditEvent.Active -> editor.update { it.copy(active = event.active) }
            FamilyItemEditEvent.Save -> viewModelScope.launch { editor.save(::write) }
            FamilyItemEditEvent.Archive -> setArchived(true)
            FamilyItemEditEvent.Restore -> setArchived(false)
            FamilyItemEditEvent.Retry -> loader.retry()
        }
    }

    private fun setArchived(archived: Boolean) {
        viewModelScope.launch { editor.run { items.setArchived(editor.value.id, archived) } }
    }

    /** Texterna sparas utan omgivande blanksteg; en ny artikel hamnar sist. */
    private suspend fun write(item: FamilyItem): Result<Unit> {
        val trimmed = item.copy(name = item.name.trim(), notes = item.notes.trim())
        return items.upsertPlaced(trimmed, isNew) { copy(sortOrder = it) }
    }
}

@Composable
fun FamilyItemEditScreen(onClose: () -> Unit, viewModel: FamilyItemEditViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val stored by viewModel.stored.collectAsStateWithLifecycle()
    FamilyItemEditContent(viewModel.isNew, state, preview, viewModel.effects, viewModel::onEvent, onClose, stored = stored)
}

/** Formuläret i mockupens ordning: namn, kategori, enhet, mängd med förhandsvisning, anteckning, aktiv. */
@Composable
fun FamilyItemEditContent(
    isNew: Boolean,
    state: EditorUiState<FamilyItem>,
    preview: PackingPreviewText?,
    effects: Flow<EditorEffect>,
    onEvent: (FamilyItemEditEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    stored: FamilyItem? = null,
) {
    val item = state.value
    EntityEditScreen(
        title = stringResource(if (isNew) R.string.family_item_new else R.string.family_item_edit),
        state = state,
        effects = effects,
        onSave = { onEvent(FamilyItemEditEvent.Save) },
        onClose = onClose,
        modifier = modifier,
        // Först när den lagrade artikeln är läst finns något att arkivera eller återställa.
        onArchive = stored?.takeUnless { it.archived }?.let { { onEvent(FamilyItemEditEvent.Archive) } },
        onRestore = stored?.takeIf { it.archived }?.let { { onEvent(FamilyItemEditEvent.Restore) } },
        onRetry = { onEvent(FamilyItemEditEvent.Retry) },
    ) {
        AppTextField(
            item.name,
            { onEvent(FamilyItemEditEvent.Name(it)) },
            stringResource(R.string.name),
            error = state.errorFor(FamilyItemValidator.NAME)?.let { stringResource(it) },
        )
        LabeledGroup(stringResource(R.string.family_item_category)) {
            ChoiceChips(FamilyItemCategory.entries, item.category, { onEvent(FamilyItemEditEvent.Category(it)) }, { stringResource(it.label()) })
        }
        LabeledGroup(stringResource(R.string.unit)) {
            ChoiceChips(ItemUnit.entries, item.unit, { onEvent(FamilyItemEditEvent.UnitChoice(it)) }, { UnitNames.name(1, it) })
        }
        LabeledGroup(stringResource(R.string.family_item_quantity)) {
            QuantityRuleRow(
                item.rule,
                { onEvent(FamilyItemEditEvent.Rule(it)) },
                unitName = { UnitNames.name(it, item.unit) },
                error = state.errorFor(FamilyItemValidator.RULE)?.let { stringResource(it) },
            )
        }
        preview?.let { PackingPreview(it.context, it.quantity, reason = it.reason) }
        AppTextField(item.notes, { onEvent(FamilyItemEditEvent.Notes(it)) }, stringResource(R.string.notes), singleLine = false)
        SwitchRow(
            stringResource(R.string.active),
            item.active,
            { onEvent(FamilyItemEditEvent.Active(it)) },
            subtitle = stringResource(R.string.active_note),
        )
    }
}
