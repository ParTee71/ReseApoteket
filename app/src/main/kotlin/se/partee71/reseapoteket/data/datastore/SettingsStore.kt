package se.partee71.reseapoteket.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalTime
import se.partee71.reseapoteket.core.engine.ReminderKind
import se.partee71.reseapoteket.core.engine.ReminderSetting
import se.partee71.reseapoteket.core.engine.ReminderSettings
import se.partee71.reseapoteket.data.common.retryWithBackoff

/** Temaval i Inställningar (SET-1). Lagras med namnet; ett okänt namn ger [System]. */
enum class ThemeMode { System, Light, Dark }

/**
 * Inställningsfilen. En trasig fil ersätts med standardvärden i stället för att varje skrivning fallerar.
 * [scope] kör filens läsningar och skrivningar – i appen på I/O-dispatchern (`AppModule`).
 */
fun settingsDataStore(
    scope: CoroutineScope,
    produceFile: () -> File,
): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    scope = scope,
    produceFile = produceFile,
)

/**
 * Ett hushåll som skickats till servern men inte bekräftats – återanvänds, även efter omstart
 * (HUS-2). Glöms när ett hushåll väljs; gäller bara [ownerUid], så det ligger kvar vid utloggning.
 */
data class PendingHousehold(val id: String, val ownerUid: String, val createdAt: Instant)

/** Enhetens inställningar (DataStore) – bara sådant som inte hör till hushållets data. */
@Singleton
class SettingsStore @Inject constructor(private val store: DataStore<Preferences>) {

    /**
     * En oläsbar inställningsfil ger standardvärden i stället för att krascha appen, och läsningen
     * försöker igen – flödet får aldrig ta slut, då slutar sessionen följa valet av hushåll.
     * Standardvärdena ges bara innan något lästs, så att ett tillfälligt fel inte nollar valet.
     */
    private val preferences: Flow<Preferences> = flow {
        var emitted = false
        store.data
            .retryWithBackoff(
                first = 1.seconds,
                max = 30.seconds,
                retryOn = { it is IOException },
                beforeRetry = { if (!emitted) emit(emptyPreferences()) },
            )
            .collect {
                emitted = true
                emit(it)
            }
    }

    val activeHouseholdId: Flow<String?> = preferences.map { it[ACTIVE_HOUSEHOLD] }

    suspend fun setActiveHouseholdId(id: String?) {
        store.edit { prefs -> if (id == null) prefs.remove(ACTIVE_HOUSEHOLD) else prefs[ACTIVE_HOUSEHOLD] = id }
    }

    /** Tar bort valet bara om det fortfarande är [ifEquals] – ett nyare val får stå kvar. */
    suspend fun clearActiveHouseholdId(ifEquals: String) {
        store.edit { prefs -> if (prefs[ACTIVE_HOUSEHOLD] == ifEquals) prefs.remove(ACTIVE_HOUSEHOLD) }
    }

    val pendingHousehold: Flow<PendingHousehold?> = preferences.map { prefs ->
        val id = prefs[PENDING_ID] ?: return@map null
        val owner = prefs[PENDING_OWNER] ?: return@map null
        val createdAt = prefs[PENDING_CREATED_AT] ?: return@map null
        PendingHousehold(id, owner, Instant.fromEpochMilliseconds(createdAt))
    }

    suspend fun setPendingHousehold(pending: PendingHousehold?) {
        store.edit { prefs ->
            if (pending == null) {
                prefs.remove(PENDING_ID)
                prefs.remove(PENDING_OWNER)
                prefs.remove(PENDING_CREATED_AT)
            } else {
                prefs[PENDING_ID] = pending.id
                prefs[PENDING_OWNER] = pending.ownerUid
                prefs[PENDING_CREATED_AT] = pending.createdAt.toEpochMilliseconds()
            }
        }
    }

    val themeMode: Flow<ThemeMode> = preferences.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[THEME_MODE] } ?: ThemeMode.System
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
    }

    /** "Flytta avprickade sist" i packlistan (SET-3); på tills användaren stänger av den. */
    val moveCheckedLast: Flow<Boolean> = preferences.map { it[MOVE_CHECKED_LAST] ?: true }

    suspend fun setMoveCheckedLast(on: Boolean) {
        store.edit { it[MOVE_CHECKED_LAST] = on }
    }

    /**
     * Telefonens påminnelser (PÅM-1): personerna och varje påminnelses inställning. Det som inte
     * ändrats – eller som är lagrat med ett ogiltigt värde – får förvalet ur [ReminderSettings].
     */
    val reminderSettings: Flow<ReminderSettings> = preferences.map { prefs ->
        ReminderKind.entries.fold(ReminderSettings(prefs[REMINDER_PERSONS].orEmpty())) { settings, kind ->
            val default = settings[kind]
            val minutes = prefs[reminderTime(kind)]?.takeIf { it in 0 until MINUTES_PER_DAY }
            val days = prefs[reminderDays(kind)]?.takeIf { it in ReminderSettings.DAYS_BEFORE }
            settings.with(
                kind,
                ReminderSetting(
                    enabled = prefs[reminderEnabled(kind)] ?: default.enabled,
                    time = minutes?.let { LocalTime(it / 60, it % 60) } ?: default.time,
                    daysBefore = days ?: default.daysBefore,
                ),
            )
        }
    }

    suspend fun setReminderPersons(personIds: Set<String>) {
        store.edit { it[REMINDER_PERSONS] = personIds }
    }

    suspend fun setReminder(kind: ReminderKind, setting: ReminderSetting) {
        store.edit {
            it[reminderEnabled(kind)] = setting.enabled
            it[reminderTime(kind)] = setting.time.hour * 60 + setting.time.minute
            it[reminderDays(kind)] = setting.daysBefore
        }
    }

    /** Hur långt påminnelserna gåtts igenom – inget före det skickas igen (PÅM-6). */
    val remindersCheckedUntil: Flow<Instant?> = preferences.map { prefs -> prefs[REMINDERS_CHECKED]?.let(Instant::fromEpochMilliseconds) }

    suspend fun setRemindersCheckedUntil(at: Instant) {
        store.edit { it[REMINDERS_CHECKED] = at.toEpochMilliseconds() }
    }

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
        val REMINDER_PERSONS = stringSetPreferencesKey("reminderPersons")
        val REMINDERS_CHECKED = longPreferencesKey("remindersCheckedUntil")

        fun reminderEnabled(kind: ReminderKind) = booleanPreferencesKey("reminder.${kind.name}.enabled")

        fun reminderTime(kind: ReminderKind) = intPreferencesKey("reminder.${kind.name}.minutes")

        fun reminderDays(kind: ReminderKind) = intPreferencesKey("reminder.${kind.name}.daysBefore")

        val ACTIVE_HOUSEHOLD = stringPreferencesKey("activeHouseholdId")
        val PENDING_ID = stringPreferencesKey("pendingHouseholdId")
        val PENDING_OWNER = stringPreferencesKey("pendingHouseholdOwner")
        val PENDING_CREATED_AT = longPreferencesKey("pendingHouseholdCreatedAt")
        val THEME_MODE = stringPreferencesKey("themeMode")
        val MOVE_CHECKED_LAST = booleanPreferencesKey("moveCheckedLast")
    }
}
