package se.partee71.reseapoteket

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.reseapoteket.reminders.TripLink

/**
 * Påminnelsens länk till en resa (PÅM-7, #88) på en riktig enhet: aliaset startar appen
 * (onCreate) och når den när den redan är igång (onNewIntent), medan en direktstart av
 * MainActivity med samma extra – som en annan app kan göra – inte öppnar något.
 */
@RunWith(AndroidJUnit4::class)
class TripLinkTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun link(tripId: String) = TripLink.intent(context, tripId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    @Test
    fun notisens_alias_startar_appen_med_resan() {
        ActivityScenario.launch<MainActivity>(link("gran-canaria")).use { scenario ->
            scenario.onActivity { assertEquals("gran-canaria", it.linkedTrip) }
        }
    }

    @Test
    fun en_direktstart_av_MainActivity_med_extran_oppnar_ingen_resa() {
        val direct = Intent(context, MainActivity::class.java).putExtra(TripLink.EXTRA_TRIP_ID, "gran-canaria")
        ActivityScenario.launch<MainActivity>(direct).use { scenario ->
            scenario.onActivity { assertNull(it.linkedTrip) }
        }
    }

    @Test
    fun notisens_alias_nar_appen_nar_den_redan_ar_igang() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var started: MainActivity
            scenario.onActivity { started = it }
            context.startActivity(link("kreta"))
            // Starten går via systemet – vänta tills länken nått aktiviteten, med en övre gräns.
            val deadline = System.currentTimeMillis() + TIMEOUT_MS
            var linked: String? = null
            while (linked == null && System.currentTimeMillis() < deadline) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { linked = it.linkedTrip }
            }
            scenario.onActivity {
                assertSame(started, it)
                assertEquals("kreta", it.linkedTrip)
                assertEquals(TripLink.ALIAS, it.intent.component?.className)
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
