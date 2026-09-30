package se.partee71.reseapoteket.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Enhetens inställningar (DataStore) – bara sådant som inte hör till hushållets data. */
@Singleton
class SettingsStore @Inject constructor(private val store: DataStore<Preferences>) {

    val activeHouseholdId: Flow<String?> = store.data.map { it[ACTIVE_HOUSEHOLD] }

    suspend fun setActiveHouseholdId(id: String?) {
        store.edit { prefs -> if (id == null) prefs.remove(ACTIVE_HOUSEHOLD) else prefs[ACTIVE_HOUSEHOLD] = id }
    }

    private companion object {
        val ACTIVE_HOUSEHOLD = stringPreferencesKey("activeHouseholdId")
    }
}
