package se.partee71.reseapoteket

import android.content.pm.PackageManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Bara startskärmen av appens egna komponenter når andra appar (skill android-intent-security,
 * #88). Bibliotekens komponenter skyddas av egna behörigheter och räknas inte här – utom Firebase
 * Auths inloggningsaktiviteter, som appen inte använder och som tas bort ur manifestet (#92, NFR-8).
 */
@RunWith(RobolectricTestRunner::class)
class ComponentExportTest {

    @Test
    fun `bara MainActivity är exporterad`() {
        val context = RuntimeEnvironment.getApplication()
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS
        val info = context.packageManager.getPackageInfo(context.packageName, flags)
        val own = MainActivity::class.java.name.substringBeforeLast('.')
        val exported = listOfNotNull(info.activities, info.receivers, info.services, info.providers)
            .flatMap { components -> components.filter { it.exported && it.name.startsWith(own) }.map { it.name } }
        assertEquals(listOf(MainActivity::class.java.name), exported)
    }

    @Test
    fun `Firebase Auths oanvända inloggningsaktiviteter finns inte`() {
        val context = RuntimeEnvironment.getApplication()
        val activities = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES)
            .activities.orEmpty().map { it.name }
        val firebaseAuth = activities.filter { it.startsWith("com.google.firebase.auth.") }
        assertEquals(emptyList(), firebaseAuth)
        // Testet ser bibliotekens aktiviteter: annars bevisar en tom lista ingenting.
        val own = MainActivity::class.java.name.substringBeforeLast('.')
        assertTrue(activities.any { !it.startsWith(own) })
    }
}
