package se.partee71.reseapoteket.core.engine

import se.partee71.reseapoteket.core.schema.TextLimits

/** Vad som händer med en rad ur en inklistrad lista (PAK-7). */
enum class PastedStatus {
    /** Läggs till. */
    ADD,

    /** Finns redan under Övrigt med samma namn – läggs inte till igen. */
    EXISTS,

    /** Längre än ett namn får vara ([TextLimits.SHORT]) – läggs inte till. */
    TOO_LONG,
}

/** En rad ur en inklistrad lista: namnet, antalet och om den läggs till. */
data class PastedLine(val name: String, val quantity: Int, val status: PastedStatus)

/**
 * En inklistrad lista, en sak per rad (PAK-7): ett tal sist blir antalet ("Solkräm 2",
 * "Solkräm x2", "Solkräm 2 st"), punkter och bockar i början tas bort, och tomma rader och
 * dubbletter (utan hänsyn till skiftläge) hoppas över. Det som redan finns ([existing]) och för
 * långa rader visas men läggs inte till.
 */
object PastedList {
    /** Hur många en egen rad kan ha – samma i listan och i "Lägg till" (PAK-4). */
    val QUANTITY_RANGE = 1..999

    // En numrerad punkt kräver mellanslag efter, så att "3.5 mm-kabel" behåller sitt namn.
    private val BULLET = Regex("""^(?:(?:[-–—•*·▪◦☐☑✓✔]|\[[ xX✓]?])\s*|\d{1,3}[.)]\s+)""")
    private val QUANTITY = Regex("""^(.*\S)\s+(?:[xX×]\s*)?(\d{1,4})\s*(?:st\.?|stycken)?$""")

    fun parse(text: String, existing: Collection<String> = emptyList()): List<PastedLine> {
        val known = existing.mapTo(HashSet()) { key(it) }
        val seen = HashSet<String>()
        return text.lines().mapNotNull { raw ->
            val line = raw.trim().replace(BULLET, "").trim()
            if (line.isEmpty()) return@mapNotNull null
            val (name, quantity) = split(line)
            if (!seen.add(key(name))) return@mapNotNull null
            val status = when {
                name.length > TextLimits.SHORT -> PastedStatus.TOO_LONG
                key(name) in known -> PastedStatus.EXISTS
                else -> PastedStatus.ADD
            }
            PastedLine(name, quantity, status)
        }
    }

    /** Namn och antal; ett tal utanför [QUANTITY_RANGE] hör till namnet. */
    private fun split(line: String): Pair<String, Int> {
        val match = QUANTITY.matchEntire(line) ?: return line to 1
        val quantity = match.groupValues[2].toInt()
        if (quantity !in QUANTITY_RANGE) return line to 1
        return match.groupValues[1].trim() to quantity
    }

    private fun key(name: String) = name.trim().lowercase()
}
