package se.partee71.reseapoteket.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.model.Household
import se.partee71.reseapoteket.core.schema.HouseholdCodec
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.suspendRunCatching
import se.partee71.reseapoteket.data.repository.HouseholdRepository
import se.partee71.reseapoteket.ui.common.EditorEvents
import se.partee71.reseapoteket.ui.common.EditorLoader
import se.partee71.reseapoteket.ui.common.EditorState
import se.partee71.reseapoteket.ui.common.EditorUiState
import se.partee71.reseapoteket.ui.common.Validator
import se.partee71.reseapoteket.ui.components.AppTextField
import se.partee71.reseapoteket.ui.components.EntityEditScreen

/** SET-2: hushållet måste ha ett namn. */
object HouseholdValidator : Validator<Household> {
    const val NAME = "name"

    override fun validate(value: Household): Map<String, Int> = if (value.name.isBlank()) mapOf(NAME to R.string.name_required) else emptyMap()
}

sealed interface HouseholdEditEvent {
    data class Name(val name: String) : HouseholdEditEvent

    data object Save : HouseholdEditEvent

    data object Retry : HouseholdEditEvent
}

/** Det aktiva hushållets namn (SET-2) – marginaldagarna ändras direkt i Inställningar. */
@HiltViewModel
class HouseholdEditViewModel @Inject constructor(private val households: HouseholdRepository) : ViewModel() {

    val editor: EditorState<Household> = EditorState(Household(id = ""), HouseholdValidator, loading = true)
    val state = editor.state
    private val loader = EditorLoader(editor, viewModelScope, { suspendRunCatching({ DataError.Unknown }) { households.current.first() } })

    fun onEvent(event: HouseholdEditEvent) {
        when (event) {
            is HouseholdEditEvent.Name -> editor.update(HouseholdValidator.NAME) { it.copy(name = event.name) }
            HouseholdEditEvent.Save -> viewModelScope.launch { editor.save(::write) }
            HouseholdEditEvent.Retry -> loader.retry()
        }
    }

    /**
     * Bara namnet skrivs – marginaldagar som ändrats under tiden står kvar. Har ett annat hushåll
     * valts under tiden finns det här inte längre att spara.
     */
    private suspend fun write(household: Household): Result<Unit> {
        val fresh = suspendRunCatching({ DataError.Unknown }) { households.current.first() }.getOrElse { return Result.failure(it) }
            ?.takeIf { it.id == household.id } ?: return Result.failure(DataError.NotFound)
        return households.update(fresh.copy(name = household.name.trim()), setOf(HouseholdCodec.NAME))
    }
}

@Composable
fun HouseholdEditScreen(onClose: () -> Unit, viewModel: HouseholdEditViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HouseholdEditContent(state, viewModel.editor, viewModel::onEvent, onClose)
}

@Composable
fun HouseholdEditContent(
    state: EditorUiState<Household>,
    events: EditorEvents,
    onEvent: (HouseholdEditEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    EntityEditScreen(
        title = stringResource(R.string.household_edit),
        state = state,
        events = events,
        onSave = { onEvent(HouseholdEditEvent.Save) },
        onClose = onClose,
        modifier = modifier,
        onRetry = { onEvent(HouseholdEditEvent.Retry) }
    ) {
        AppTextField(
            state.value.name,
            { onEvent(HouseholdEditEvent.Name(it)) },
            stringResource(R.string.name),
            error = state.errorFor(HouseholdValidator.NAME)?.let { stringResource(it) }
        )
    }
}
