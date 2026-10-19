package se.partee71.reseapoteket.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.testing.captureLightAndDark
import se.partee71.ui.components.AppButton
import se.partee71.ui.components.AppSnackbarHost
import se.partee71.ui.components.ButtonVariant
import se.partee71.ui.theme.Spacing

/** Beteende och skärmdumpar (ljust + mörkt) för de delade komponenterna. */
@RunWith(RobolectricTestRunner::class)
class ComponentsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `AppButton - varianter och lägen`() = captureLightAndDark("AppButton_varianter") {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            AppButton("Logga in med Google", {}, Modifier.fillMaxWidth(), icon = R.drawable.ic_login)
            AppButton("Loggar in …", {}, Modifier.fillMaxWidth(), loading = true)
            AppButton("Lägg till", {}, variant = ButtonVariant.Secondary)
            AppButton("Avbryt", {}, variant = ButtonVariant.Text)
            AppButton("Spara", {}, enabled = false)
        }
    }

    @Test
    fun `AppSnackbarHost - meddelande`() = rule.captureLightAndDark("AppSnackbarHost_meddelande") {
        val state = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { state.showSnackbar("Något gick fel. Försök igen.", duration = SnackbarDuration.Indefinite) }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.BottomCenter) {
            AppSnackbarHost(state)
        }
    }

    @Test
    fun `EmptyState - i en lista`() = captureLightAndDark("EmptyState_lista") {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            EmptyState(
                icon = R.drawable.ic_luggage,
                title = "Inga resor än",
                message = "Lägg till en resa så räknar appen ut vad ni ska packa.",
                action = { AppButton("Lägg till resa", {}, Modifier.fillMaxWidth()) }
            )
        }
    }

    @Test
    fun `EmptyState - helskärm med fotnot`() = captureLightAndDark("EmptyState_helskarm") {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            EmptyState(
                icon = R.drawable.ic_download,
                title = "Uppdatera appen",
                message = "Installera den senaste versionen för att fortsätta.",
                note = "Ingenting ändras förrän appen är uppdaterad.",
                fullScreen = true,
                action = { AppButton("Hämta senaste versionen", {}, Modifier.fillMaxWidth()) }
            )
        }
    }
}
