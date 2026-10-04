package se.partee71.reseapoteket

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.VisibleForTesting
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import se.partee71.reseapoteket.core.engine.ReminderKind
import se.partee71.reseapoteket.data.datastore.SettingsStore
import se.partee71.reseapoteket.data.datastore.ThemeMode
import se.partee71.reseapoteket.reminders.ReminderSync
import se.partee71.reseapoteket.reminders.TripLink
import se.partee71.reseapoteket.ui.AppRoot
import se.partee71.reseapoteket.ui.components.LocalTravelDayMorning
import se.partee71.reseapoteket.ui.theme.ReseApoteketTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsStore

    @Inject lateinit var reminders: ReminderSync

    /** Resan som en påminnelse öppnar (PÅM-7), tills navigationen visat den. */
    private val openTrip = MutableStateFlow<String?>(null)

    /** Den senaste resa som en länk faktiskt öppnade – för instrumenttestet av [TripLink]. */
    @VisibleForTesting
    internal var linkedTrip: String? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bara vid en ny start – efter en omstart av aktiviteten står navigationen redan där den var.
        if (savedInstanceState == null) takeTrip(intent)
        enableEdgeToEdge()
        // Medan appen syns flyttar varje ändring nästa påminnelse (PÅM-6).
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { reminders.follow() } }
        setContent {
            // null tills inställningen lästs (millisekunder): då syns fönstrets bakgrund i stället
            // för en blixt i fel tema.
            val mode by settings.themeMode.collectAsStateWithLifecycle(initialValue = null)
            val dark = when (mode ?: return@setContent) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // Statusradens ikoner följer appens tema, inte bara systemets (SET-1).
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            val trip by openTrip.collectAsStateWithLifecycle()
            // Resedagens morgon visas där något packas på resedagen (PAK-6).
            val morning by remember { settings.reminderSettings.map { it[ReminderKind.TRAVEL_DAY].time } }
                .collectAsStateWithLifecycle(initialValue = LocalTravelDayMorning.current)
            ReseApoteketTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalTravelDayMorning provides morning) {
                    AppRoot(openTrip = trip, onTripOpened = { openTrip.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeTrip(intent)
    }

    /** Bara notisens egen länk öppnar en resa ([TripLink]). */
    private fun takeTrip(intent: Intent) {
        TripLink.tripId(intent)?.let {
            linkedTrip = it
            openTrip.value = it
        }
        TripLink.consume(intent)
    }
}
