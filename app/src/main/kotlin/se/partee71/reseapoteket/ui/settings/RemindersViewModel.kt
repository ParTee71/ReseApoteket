package se.partee71.reseapoteket.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import se.partee71.reseapoteket.core.engine.Reminder
import se.partee71.reseapoteket.core.engine.ReminderKind
import se.partee71.reseapoteket.core.engine.ReminderSetting
import se.partee71.reseapoteket.core.engine.ReminderSettings
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.data.common.withFallback
import se.partee71.reseapoteket.data.datastore.SettingsStore
import se.partee71.reseapoteket.data.repository.PersonRepository
import se.partee71.reseapoteket.reminders.ReminderRunner
import se.partee71.reseapoteket.reminders.ReminderSource
import se.partee71.ui.common.STOP_TIMEOUT_MILLIS

/** Kortet Påminnelser: personerna att välja bland, telefonens inställningar och nästa påminnelse av varje slag. */
@Immutable
data class RemindersUiState(
    val persons: ImmutableList<Person> = persistentListOf(),
    val settings: ReminderSettings = ReminderSettings(),
    val next: ImmutableMap<ReminderKind, Reminder> = persistentMapOf()
)

sealed interface RemindersEvent {
    data class TogglePerson(val personId: String) : RemindersEvent

    data class Change(val kind: ReminderKind, val setting: ReminderSetting) : RemindersEvent
}

/** Påminnelserna i Inställningar (PÅM-1): sparas på telefonen och flyttar nästa påminnelse direkt. */
@HiltViewModel
class RemindersViewModel @Inject constructor(
    persons: PersonRepository,
    source: ReminderSource,
    private val settings: SettingsStore,
    private val runner: ReminderRunner,
    private val clock: Clock,
    private val zone: TimeZone
) : ViewModel() {

    // Personer och plan är tillägg: går de inte att läsa syns inställningarna ändå.
    val state: StateFlow<RemindersUiState> = combine(
        persons.observe().map { all -> all.filterNot { it.archived }.sortedBy { it.sortOrder } }.withFallback(emptyList()),
        settings.reminderSettings,
        source.plan.withFallback(emptyList())
    ) { people, chosen, planned ->
        val now = clock.now()
        val upcoming = planned.filter { it.at.toInstant(zone) > now }
        RemindersUiState(people.toImmutableList(), chosen, ReminderKind.entries.mapNotNull { kind -> upcoming.firstOrNull { it.kind == kind }?.let { kind to it } }.toMap().toImmutableMap())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RemindersUiState())

    fun onEvent(event: RemindersEvent) {
        viewModelScope.launch {
            when (event) {
                is RemindersEvent.TogglePerson -> {
                    val chosen = settings.reminderSettings.first().personIds
                    settings.setReminderPersons(if (event.personId in chosen) chosen - event.personId else chosen + event.personId)
                }

                is RemindersEvent.Change -> settings.setReminder(event.kind, event.setting)
            }
            runner.settingsChanged()
        }
    }
}
