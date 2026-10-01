package se.partee71.reseapoteket

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import se.partee71.reseapoteket.data.datastore.SettingsStore
import se.partee71.reseapoteket.data.datastore.ThemeMode
import se.partee71.reseapoteket.ui.AppRoot
import se.partee71.reseapoteket.ui.theme.ReseApoteketTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
            ReseApoteketTheme(darkTheme = dark) {
                AppRoot()
            }
        }
    }
}
