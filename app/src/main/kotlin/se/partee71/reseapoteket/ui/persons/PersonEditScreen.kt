package se.partee71.reseapoteket.ui.persons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.repository.PersonRepository
import se.partee71.reseapoteket.ui.common.EditorEffect
import se.partee71.reseapoteket.ui.common.EditorState
import se.partee71.reseapoteket.ui.common.EditorUiState
import se.partee71.reseapoteket.ui.common.Validator
import se.partee71.reseapoteket.ui.components.AppTextField
import se.partee71.reseapoteket.ui.components.AvatarSize
import se.partee71.reseapoteket.ui.components.ColorSwatchPicker
import se.partee71.reseapoteket.ui.components.EmojiPicker
import se.partee71.reseapoteket.ui.components.EntityEditScreen
import se.partee71.reseapoteket.ui.components.GroupLabel
import se.partee71.reseapoteket.ui.components.PERSON_EMOJIS
import se.partee71.reseapoteket.ui.components.PersonAvatar
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.reseapoteket.ui.theme.Spacing

/** PER-2: namnet är obligatoriskt; emoji och färg har alltid ett värde. */
object PersonValidator : Validator<Person> {
    const val NAME = "name"

    override fun validate(value: Person): Map<String, Int> =
        if (value.name.isBlank()) mapOf(NAME to R.string.person_name_required) else emptyMap()
}

sealed interface PersonEditEvent {
    data class Name(val name: String) : PersonEditEvent

    data class Emoji(val emoji: String) : PersonEditEvent

    data class PersonColor(val hex: String) : PersonEditEvent

    data object Save : PersonEditEvent

    data object Retry : PersonEditEvent
}

/** Skapa ([personId] = `null`) eller redigera en person (PER-2). */
@HiltViewModel(assistedFactory = PersonEditViewModel.Factory::class)
class PersonEditViewModel @AssistedInject constructor(
    @Assisted private val personId: String?,
    private val persons: PersonRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(personId: String?): PersonEditViewModel
    }

    val isNew: Boolean = personId == null

    val editor: EditorState<Person> = EditorState(
        Person(id = personId ?: persons.newId(), emoji = PERSON_EMOJIS.first(), colorHex = AppColors.PERSON_HEX.first()),
        PersonValidator,
        loading = !isNew,
    )
    val state = editor.state
    val effects: Flow<EditorEffect> = editor.effects

    init {
        load()
    }

    fun onEvent(event: PersonEditEvent) {
        when (event) {
            is PersonEditEvent.Name -> editor.update(PersonValidator.NAME) { it.copy(name = event.name) }
            is PersonEditEvent.Emoji -> editor.update { it.copy(emoji = event.emoji) }
            is PersonEditEvent.PersonColor -> editor.update { it.copy(colorHex = event.hex) }
            PersonEditEvent.Save -> viewModelScope.launch { editor.save(::write) }
            PersonEditEvent.Retry -> {
                editor.reload()
                load()
            }
        }
    }

    private fun load() {
        val id = personId ?: return
        viewModelScope.launch {
            persons.get(id)
                .onSuccess { person -> if (person == null) editor.loadFailed(DataError.Unknown) else editor.load(person.withDefaults()) }
                .onFailure { editor.loadFailed(it as? DataError ?: DataError.Unknown) }
        }
    }

    /**
     * Saknas emoji eller färg (äldre eller trasigt dokument) får formuläret förvalen, så att
     * avataren aldrig blir tom; ett värde som appen inte känner till (från en nyare app) står kvar.
     */
    private fun Person.withDefaults(): Person = copy(
        emoji = emoji.ifBlank { PERSON_EMOJIS.first() },
        colorHex = colorHex.ifBlank { AppColors.PERSON_HEX.first() },
    )

    /** En ny person hamnar sist i listan. */
    private suspend fun write(person: Person): Result<Unit> {
        val toWrite = if (isNew) {
            person.copy(name = person.name.trim(), sortOrder = persons.getAll().getOrNull()?.maxOfOrNull { it.sortOrder }?.plus(1) ?: 0)
        } else {
            person.copy(name = person.name.trim())
        }
        return persons.upsert(toWrite)
    }
}

@Composable
fun PersonEditScreen(onClose: () -> Unit, viewModel: PersonEditViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PersonEditContent(viewModel.isNew, state, viewModel.effects, viewModel::onEvent, onClose)
}

@Composable
fun PersonEditContent(
    isNew: Boolean,
    state: EditorUiState<Person>,
    effects: Flow<EditorEffect>,
    onEvent: (PersonEditEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EntityEditScreen(
        title = stringResource(if (isNew) R.string.person_new else R.string.person_edit),
        state = state,
        effects = effects,
        onSave = { onEvent(PersonEditEvent.Save) },
        onClose = onClose,
        modifier = modifier,
        onRetry = { onEvent(PersonEditEvent.Retry) },
    ) {
        val person = state.value
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PersonAvatar(person.emoji, AppColors.person(person.colorHex), size = AvatarSize.Large)
        }
        AppTextField(
            person.name,
            { onEvent(PersonEditEvent.Name(it)) },
            stringResource(R.string.person_name),
            error = state.errorFor(PersonValidator.NAME)?.let { stringResource(it) },
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            GroupLabel(stringResource(R.string.person_emoji))
            EmojiPicker(person.emoji, { onEvent(PersonEditEvent.Emoji(it)) })
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            GroupLabel(stringResource(R.string.person_color))
            ColorSwatchPicker(person.colorHex, { onEvent(PersonEditEvent.PersonColor(it)) })
        }
    }
}
