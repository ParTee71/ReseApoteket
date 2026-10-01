package se.partee71.reseapoteket.ui.persons

import androidx.annotation.DrawableRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.engine.DoseScheduleFormatter
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.Regimen
import se.partee71.reseapoteket.core.model.RegimenKind
import se.partee71.reseapoteket.core.model.displayName
import se.partee71.reseapoteket.core.model.listed
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.dataError
import se.partee71.reseapoteket.data.repository.PersonRepository
import se.partee71.reseapoteket.data.repository.RegimenRepository
import se.partee71.reseapoteket.ui.common.DetailLoader
import se.partee71.reseapoteket.ui.common.DetailUiState
import se.partee71.reseapoteket.ui.components.ApotekCard
import se.partee71.reseapoteket.ui.components.AppButton
import se.partee71.reseapoteket.ui.components.AppDivider
import se.partee71.reseapoteket.ui.components.AppMenuItem
import se.partee71.reseapoteket.ui.components.AvatarSize
import se.partee71.reseapoteket.ui.components.ButtonVariant
import se.partee71.reseapoteket.ui.components.DetailHeader
import se.partee71.reseapoteket.ui.components.EntityDetailScreen
import se.partee71.reseapoteket.ui.components.ItemRow
import se.partee71.reseapoteket.ui.components.PersonAvatar
import se.partee71.reseapoteket.ui.components.SectionHeader
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.reseapoteket.ui.theme.AppTypography
import se.partee71.reseapoteket.ui.theme.Tone

/** En person med sina recept och kosttillskott, utan arkiverade (PER-4). */
data class PersonDetail(val person: Person, val medicines: List<Regimen>, val supplements: List<Regimen>)

sealed interface PersonDetailEvent {
    data object Retry : PersonDetailEvent

    data class SetArchived(val archived: Boolean) : PersonDetailEvent

    data object ErrorShown : PersonDetailEvent
}

@HiltViewModel(assistedFactory = PersonDetailViewModel.Factory::class)
class PersonDetailViewModel @AssistedInject constructor(
    @Assisted private val personId: String,
    private val persons: PersonRepository,
    regimens: RegimenRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(personId: String): PersonDetailViewModel
    }

    private val loader = DetailLoader(
        combine(persons.observe(personId), regimens.forPerson(personId).observe()) { person, all ->
            person?.let {
                PersonDetail(it, all.listed(RegimenKind.MEDICINE), all.listed(RegimenKind.SUPPLEMENT))
            }
        },
        viewModelScope,
    )
    val state = loader.state

    /** Ett misslyckat arkivera/återställ, tills skärmen visat det. */
    private val _error = MutableStateFlow<DataError?>(null)
    val error: StateFlow<DataError?> = _error.asStateFlow()

    fun onEvent(event: PersonDetailEvent) {
        when (event) {
            PersonDetailEvent.Retry -> loader.retry()
            is PersonDetailEvent.SetArchived -> viewModelScope.launch {
                persons.setArchived(personId, event.archived).dataError()?.let { _error.value = it }
            }
            PersonDetailEvent.ErrorShown -> _error.value = null
        }
    }
}

@Composable
fun PersonDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddRegimen: (RegimenKind) -> Unit,
    onOpenRegimen: (Regimen) -> Unit,
    viewModel: PersonDetailViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    PersonDetailContent(state, viewModel::onEvent, onBack, onEdit, onAddRegimen, onOpenRegimen, error = error)
}

@Composable
fun PersonDetailContent(
    state: DetailUiState<PersonDetail>,
    onEvent: (PersonDetailEvent) -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddRegimen: (RegimenKind) -> Unit,
    onOpenRegimen: (Regimen) -> Unit,
    modifier: Modifier = Modifier,
    error: DataError? = null,
) {
    val archive = stringResource(R.string.archive)
    val restore = stringResource(R.string.restore)
    val archived = stringResource(R.string.person_archived)
    val summary = (state as? DetailUiState.Content)?.value?.let { personSummary(it.medicines.size, it.supplements.size) }
    EntityDetailScreen(
        state = state,
        header = { DetailHeader(it.person.name, if (it.person.archived) archived else summary) },
        onBack = onBack,
        modifier = modifier,
        onEdit = onEdit,
        menu = {
            // Arkiverad står personen kvar här med "Återställ" – inget behöver ångras (PER-3).
            if (it.person.archived) {
                listOf(AppMenuItem(restore, { onEvent(PersonDetailEvent.SetArchived(false)) }, R.drawable.ic_unarchive))
            } else {
                listOf(AppMenuItem(archive, { onEvent(PersonDetailEvent.SetArchived(true)) }, R.drawable.ic_archive))
            }
        },
        onRetry = { onEvent(PersonDetailEvent.Retry) },
        error = error,
        onErrorShown = { onEvent(PersonDetailEvent.ErrorShown) },
        leading = { PersonAvatar(it.person.emoji, AppColors.person(it.person.colorHex), size = AvatarSize.Large) },
    ) { detail ->
        RegimenSection(
            title = stringResource(R.string.person_medicines_title),
            icon = R.drawable.ic_medication,
            tone = Tone.Primary,
            regimens = detail.medicines,
            none = stringResource(R.string.person_medicines_none),
            addLabel = stringResource(R.string.person_medicines_add),
            onAdd = { onAddRegimen(RegimenKind.MEDICINE) },
            onOpen = onOpenRegimen,
        )
        RegimenSection(
            title = stringResource(R.string.person_supplements_title),
            icon = R.drawable.ic_eco,
            tone = Tone.Green,
            regimens = detail.supplements,
            none = stringResource(R.string.person_supplements_none),
            addLabel = stringResource(R.string.person_supplements_add),
            onAdd = { onAddRegimen(RegimenKind.SUPPLEMENT) },
            onOpen = onOpenRegimen,
        )
    }
}

/** Recept eller kosttillskott i ett kort: rubrik med antal, raderna och "Lägg till" (PER-4). */
@Composable
private fun RegimenSection(
    title: String,
    @DrawableRes icon: Int,
    tone: Tone,
    regimens: List<Regimen>,
    none: String,
    addLabel: String,
    onAdd: () -> Unit,
    onOpen: (Regimen) -> Unit,
) {
    ApotekCard {
        SectionHeader(title, icon = icon, count = regimens.size.toString(), tone = tone)
        if (regimens.isEmpty()) {
            Text(none, style = AppTypography.itemSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        regimens.forEachIndexed { index, regimen ->
            if (index > 0) AppDivider()
            ItemRow(
                title = regimen.displayName,
                subtitle = DoseScheduleFormatter.format(regimen.schedule, regimen.unit),
                onClick = { onOpen(regimen) },
                navigates = true,
            )
        }
        AppButton(addLabel, onAdd, variant = ButtonVariant.Text, icon = R.drawable.ic_add)
    }
}
