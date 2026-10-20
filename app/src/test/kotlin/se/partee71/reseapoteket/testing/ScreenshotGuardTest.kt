package se.partee71.reseapoteket.testing

import java.io.File
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Vakt för `captureLightAndDark` utan compose-regel: innehåll med oändlig animation hänger
 * Roborazzis `idle()` när klassen körs först i JVM:en (Gradle kör förra körningens fallerade
 * klasser först), så testtasken når sin tidsgräns. Sådant innehåll fotograferas i en klass med
 * `createComposeRule()` – se dokumentationen i Screenshots.kt.
 */
class ScreenshotGuardTest {

    private val testRoot = File("").absoluteFile.let { if (it.name == "app") it else File(it, "app") }.resolve("src/test/kotlin")

    @Test
    fun `klasser utan compose-regel fotograferar inget med oändlig animation`() {
        val offenders = testRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "Screenshots.kt" && it.name != "ScreenshotGuardTest.kt" }
            .filter { STANDALONE.containsMatchIn(it.readText()) && "createComposeRule" !in it.readText() }
            .flatMap { file -> ANIMATED.findAll(file.readText()).map { "${file.name}: ${it.value}" } }
            .toList()
        assertTrue(offenders.isEmpty(), "Flytta bilden till en klass med createComposeRule(): $offenders")
    }

    private companion object {
        /** Den fristående varianten – inte `rule.captureLightAndDark`. */
        val STANDALONE = Regex("""(?<![.\w])captureLightAndDark\(""")

        /** Komponenter vars animation aldrig tar slut. */
        val ANIMATED = Regex("""\b(AppLoading|LoadingIndicator|\w*WavyProgressIndicator|SuitcaseProgress|ComponentGallery)\(|loading = true""")
    }
}
