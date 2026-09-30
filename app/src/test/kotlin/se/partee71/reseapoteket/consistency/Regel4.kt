package se.partee71.reseapoteket.consistency

/**
 * Tolkning av `ui-forbidden.txt` och `ui-allowlist.txt` – speglar hooken
 * `.claude/hooks/regel4-check.mjs` rad för rad. Att de tolkar lika bevisas av att både
 * hooktestet och [UiConsistencyTest] kör exemplen i `ui-forbidden-examples.txt`.
 */
object Regel4 {

    data class Rule(val regex: Regex, val replacement: String)

    data class Allowed(val file: String, val symbol: String)

    data class Hit(val line: Int, val match: String, val replacement: String, val scope: String)

    fun readResource(name: String): String =
        checkNotNull(Regel4::class.java.getResource("/$name")) { "$name saknas i testresurserna" }.readText()

    /** Sektionerad fil: `[namn]` följt av rader; tomma rader och `#`-rader hoppas över. */
    fun parseSections(text: String): Map<String, List<String>> {
        val sections = linkedMapOf<String, MutableList<String>>()
        var current: MutableList<String>? = null
        for (line in text.lines()) {
            if (line.isBlank() || line.startsWith("#")) continue
            val section = Regex("""^\[(\w+)]\s*$""").find(line)
            if (section != null) {
                current = mutableListOf<String>().also { sections[section.groupValues[1]] = it }
            } else {
                current?.add(line)
            }
        }
        return sections
    }

    fun parsePatterns(text: String): Map<String, List<Rule>> =
        parseSections(text).mapValues { (_, lines) ->
            lines.map { line ->
                val parts = line.split("\t")
                Rule(Regex(parts[0]), parts.getOrElse(1) { "" })
            }
        }

    fun parseAllowlist(text: String): List<Allowed> =
        text.lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val location = line.split(Regex("""\s+[–-]\s+"""))[0]
                val idx = location.lastIndexOf(':')
                Allowed(location.substring(0, idx).trim(), location.substring(idx + 1).trim())
            }

    /** `feature`: ui-paket utom components/theme/common (även filer direkt i ui/). `firestore`: allt utom data/firestore/. */
    fun scopesFor(relPath: String): List<String> {
        val p = relPath.replace('\\', '/')
        if (!p.startsWith("app/src/main/") || !p.endsWith(".kt")) return emptyList()
        val scopes = mutableListOf<String>()
        val ui = Regex("""/ui/(?:([^/]+)/)?[^/]+\.kt$|/ui/([^/]+)/""").find(p)
        if (ui != null) {
            val sub = ui.groupValues[1].ifEmpty { ui.groupValues[2] }
            if (sub !in setOf("components", "theme", "common")) scopes += "feature"
        }
        if (!p.contains("/data/firestore/")) scopes += "firestore"
        return scopes
    }

    fun findViolations(
        relPath: String,
        content: String,
        patterns: Map<String, List<Rule>>,
        allowlist: List<Allowed>,
    ): List<Hit> {
        val active = scopesFor(relPath).filter { it in patterns }
        if (active.isEmpty()) return emptyList()
        val normalized = relPath.replace('\\', '/')
        return content.lines().flatMapIndexed { i, line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("import ")) {
                return@flatMapIndexed emptyList()
            }
            active.flatMap { scope ->
                patterns.getValue(scope).mapNotNull { rule ->
                    val match = rule.regex.find(line) ?: return@mapNotNull null
                    val allowed = allowlist.any { it.file == normalized && line.contains(it.symbol) }
                    if (allowed) null else Hit(i + 1, match.value, rule.replacement, scope)
                }
            }
        }
    }
}
