package se.partee71.reseapoteket.data.export

import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Exportfilen med hälsodata ligger inte kvar i cachen (SET-5, #88). */
@RunWith(RobolectricTestRunner::class)
class CacheExportFilesTest {

    private val context = RuntimeEnvironment.getApplication()
    private val files = CacheExportFiles(context, UnconfinedTestDispatcher())

    @Test
    fun `exporten finns kvar en timme för delningen och rensas sedan`() = runTest {
        files.save("reseapoteket-export.json", """{"documents":[]}""").getOrThrow()
        val file = File(context.cacheDir, "exports/reseapoteket-export.json")
        assertTrue(file.exists())
        val written = Instant.fromEpochMilliseconds(file.lastModified())
        files.cleanUp(written + 59.minutes)
        assertTrue(file.exists(), "nyss delad")
        files.cleanUp(written + 60.minutes)
        assertFalse(file.exists())
        // Ingen export alls är inget fel.
        files.cleanUp(written + 120.minutes)
    }
}
