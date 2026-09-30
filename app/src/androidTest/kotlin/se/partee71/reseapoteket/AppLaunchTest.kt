package se.partee71.reseapoteket

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Appen startar på en riktig enhet med Hilt och Expressive-temat (instrumented.yml). */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun startskarmen_visas() {
        val appName = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.app_name)
        compose.onNodeWithText(appName).assertIsDisplayed()
    }
}
