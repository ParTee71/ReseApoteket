package se.partee71.reseapoteket

import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.reseapoteket.reminders.TripLink

/**
 * Notisens alias (PÅM-7, #88) når den redan startade appen på en riktig enhet: samma
 * MainActivity får länken via onNewIntent, och dess intent är därefter länken (setIntent).
 */
@RunWith(AndroidJUnit4::class)
class TripLinkTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun notisens_alias_nar_appen_nar_den_redan_ar_igang() {
        val started = compose.activity
        val link = TripLink.intent(started, "gran-canaria")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        started.startActivity(link)
        compose.waitUntil(TIMEOUT_MS) { compose.activity.intent.component?.className == TripLink.ALIAS }
        assertSame(started, compose.activity)
        assertEquals(TripLink.ALIAS, compose.activity.intent.component?.className)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
