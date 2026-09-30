package se.partee71.reseapoteket.consistency

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Regel 4 i bygget (skill shared-ui-components). Mönstren står bara i `ui-forbidden.txt`,
 * undantagen bara i `ui-allowlist.txt` – samma filer som hooken `regel4-check` läser.
 */
class UiConsistencyTest {

    private val repoRoot = File("").absoluteFile.let { if (it.name == "app") it.parentFile else it }
    private val patterns = Regel4.parsePatterns(Regel4.readResource("ui-forbidden.txt"))
    private val allowlist = Regel4.parseAllowlist(Regel4.readResource("ui-allowlist.txt"))
    private val examples = Regel4.parseSections(Regel4.readResource("ui-forbidden-examples.txt"))
    private val production = Konsist.scopeFromProduction(moduleName = "app", sourceSetName = "main")

    @Test
    fun `mönsterfilen har båda scopen och en ersättning per mönster`() {
        assertEquals(setOf("feature", "firestore"), patterns.keys)
        patterns.values.flatten().forEach { assertTrue(it.replacement.isNotBlank(), it.regex.pattern) }
    }

    @Test
    fun `ingen kod i app-src-main använder förbjudna mönster`() {
        val hits = production.files.flatMap { file ->
            val rel = File(file.path).relativeTo(repoRoot).invariantSeparatorsPath
            Regel4.findViolations(rel, file.text, patterns, allowlist).map { hit ->
                "  $rel:${hit.line}  `${hit.match.trim()}`  → använd ${hit.replacement}"
            }
        }
        if (hits.isNotEmpty()) {
            fail(
                "Regel 4: förbjudna mönster (skill shared-ui-components).\n" + hits.joinToString("\n") +
                    "\nUndantag bara via ui-allowlist.txt med motivering och användarens ok.",
            )
        }
    }

    @Test
    fun `delade exempel tolkas likadant som i hooken`() {
        val featureFile = "app/src/main/kotlin/se/partee71/reseapoteket/ui/trips/TripRow.kt"
        examples.getValue("ok").forEach { line ->
            assertEquals(emptyList(), Regel4.findViolations(featureFile, line, patterns, allowlist), line)
        }
        examples.getValue("stopp").forEach { line ->
            assertTrue(Regel4.findViolations(featureFile, line, patterns, emptyList()).isNotEmpty(), line)
        }
        examples.getValue("scope").forEach { line ->
            val parts = line.split("\t")
            val expected = parts.getOrElse(1) { "" }.split(",").filter { it.isNotEmpty() }
            assertEquals(expected, Regel4.scopesFor(parts[0]), parts[0])
        }
    }

    @Test
    fun `allowlist-rad släpper igenom just den filen och symbolen`() {
        val file = "app/src/main/kotlin/se/partee71/reseapoteket/ui/persons/PersonRow.kt"
        val allowed = Regel4.parseAllowlist("$file:Card( – test")
        assertEquals(emptyList(), Regel4.findViolations(file, "Card(modifier) { }", patterns, allowed))
        assertTrue(Regel4.findViolations(file, "Button(onClick = {})", patterns, allowed).isNotEmpty())
    }

    @Test
    fun `redigera-ViewModels exponerar EditorState`() {
        production.classes().withNameEndingWith("EditViewModel").forEach { vm ->
            assertTrue(
                vm.properties().any { it.type?.name?.startsWith("EditorState") == true },
                "${vm.name} ska exponera en EditorState (ram EntityEditScreen)",
            )
        }
    }

    @Test
    fun `komponenttabellen i shared-ui-components stämmer med ui-components`() {
        val inCode = production.functions()
            .filter { it.resideInPackage("..ui.components..") && it.hasPublicOrDefaultModifier }
            .filter { it.hasAnnotationWithName("Composable") }
            .map { it.name }
            .toSet()
        assumeTrue(
            "Dokumentdrift: hoppar över – ui.components saknar publika composables (aktiveras i #20)".also {
                if (inCode.isEmpty()) println(it)
            },
            inCode.isNotEmpty(),
        )
        val inTable = documentedComponents()
        val missingInTable = inCode - inTable
        val missingInCode = inTable - inCode
        assertTrue(missingInTable.isEmpty(), "Lägg till raden i shared-ui-components: $missingInTable")
        assertTrue(missingInCode.isEmpty(), "Ta bort raden i shared-ui-components eller bygg komponenten: $missingInCode")
    }

    /** Komponentnamnen i kolumn två i tabellerna "Utseende" och "Beteende". */
    private fun documentedComponents(): Set<String> {
        val skill = File(repoRoot, ".claude/skills/shared-ui-components/SKILL.md").readText()
        val tables = Regex("""## (Utseende|Beteende)[^\n]*\n(.*?)(?=\n## )""", RegexOption.DOT_MATCHES_ALL)
            .findAll(skill)
            .joinToString("\n") { it.groupValues[2] }
        return tables.lines()
            .filter { it.startsWith("|") }
            .mapNotNull { row -> row.split("|").getOrNull(2)?.let { Regex("`(\\w+)`").find(it)?.groupValues?.get(1) } }
            .toSet()
    }
}
