package se.partee71.reseapoteket.core.medicine

import java.text.Normalizer
import kotlinx.datetime.LocalDate
import se.partee71.reseapoteket.core.model.ItemUnit
import se.partee71.reseapoteket.core.model.MedicineForm
import se.partee71.reseapoteket.core.model.Regimen

/**
 * Ett läkemedel i Läkemedelsverkets lista över läkemedel som säljs i Sverige, så som appen behöver
 * det: "Alvedon" · "500 mg" · "Filmdragerad tablett" ([formText]), som receptets [form].
 */
data class MedicineEntry(val name: String, val strength: String, val formText: String) {
    val form: MedicineForm get() = MedicineForms.of(formText)

    /** "Alvedon 500 mg" – det förslaget visar och sökningen matchar mot. */
    val title: String get() = listOf(name, strength).filter { it.isNotBlank() }.joinToString(" ")
}

/** En träff och var i [MedicineEntry.title] den matchar – för markeringen i förslaget. */
data class MedicineMatch(val entry: MedicineEntry, val start: Int, val length: Int)

/**
 * Det inbyggda utdraget ur Läkemedelsverkets lista (REC-6): sök på namn medan man skriver. Helt lokalt – inga
 * nätverksanrop, inget loggas. Filen byggs av `tools/medicines` och läses av [parse].
 */
class MedicineCatalog(val updated: LocalDate?, val entries: List<MedicineEntry>) {

    private val keys = entries.map { fold(it.title) }

    /**
     * Högst [limit] läkemedel vars namn med styrka ([MedicineEntry.title]) eller något ord i det
     * börjar med [query] – "levaxin 50" hittar Levaxin 50 mikrogram – utan hänsyn till skiftläge och
     * diakritiska tecken. Namnets början först, sedan ordstart; annars filens
     * ordning (svensk namnordning, minst styrka först). Under [MIN_QUERY] tecken ger inget.
     */
    fun search(query: String, limit: Int = LIMIT): List<MedicineMatch> {
        val needle = fold(query.trim())
        if (needle.length < MIN_QUERY) return emptyList()
        val first = mutableListOf<MedicineMatch>()
        val other = mutableListOf<MedicineMatch>()
        for (i in entries.indices) {
            val at = wordStart(keys[i], needle) ?: continue
            (if (at == 0) first else other) += MedicineMatch(entries[i], at, needle.length)
            if (first.size >= limit) break
        }
        return (first + other).take(limit)
    }

    companion object {
        const val MIN_QUERY = 3
        const val LIMIT = 5
        private const val HEADER = "#"
        private const val UPDATED = "# updated "

        /**
         * Filformatet: en rad per läkemedel, tabbseparerat `namn  styrka  form`; rader som börjar
         * med `#` är kommentarer (`# updated 2026-10-04`). Rader utan namn eller form hoppas över, och
         * fler kolumner än tre ignoreras – en äldre app klarar en nyare fil.
         */
        fun parse(text: String): MedicineCatalog {
            var updated: LocalDate? = null
            val entries = text.lineSequence().mapNotNull { line ->
                if (line.startsWith(UPDATED)) updated = runCatching { LocalDate.parse(line.removePrefix(UPDATED).trim()) }.getOrNull()
                if (line.isBlank() || line.startsWith(HEADER)) return@mapNotNull null
                val cols = line.split('\t')
                if (cols.size < 3 || cols[0].isBlank() || cols[2].isBlank()) return@mapNotNull null
                MedicineEntry(name = cols[0].trim(), strength = cols[1].trim(), formText = cols[2].trim())
            }.toList()
            return MedicineCatalog(updated, entries)
        }

        /** Gemener utan diakritiska tecken, tecken för tecken – så att positionerna stämmer med originalet. */
        internal fun fold(text: String): String = buildString(text.length) {
            text.forEach { c -> append(Normalizer.normalize(c.toString(), Normalizer.Form.NFD).first().lowercaseChar()) }
        }

        private fun wordStart(haystack: String, needle: String): Int? {
            var from = 0
            while (true) {
                val at = haystack.indexOf(needle, from)
                if (at < 0) return null
                if (at == 0 || !haystack[at - 1].isLetterOrDigit()) return at
                from = at + 1
            }
        }
    }
}

/** Enheten ett recept med formen räknas i när det fylls i från listan (REC-7) – formuläret byter annars aldrig enhet vid formbyte. */
val MedicineForm.defaultUnit: ItemUnit
    get() = when (this) {
        MedicineForm.TABLET -> ItemUnit.TABLETT
        MedicineForm.CAPSULE -> ItemUnit.KAPSEL
        MedicineForm.LIQUID, MedicineForm.DROPS -> ItemUnit.ML
        MedicineForm.POWDER -> ItemUnit.DOS
        MedicineForm.INHALER -> ItemUnit.PUFF
        MedicineForm.PATCH, MedicineForm.OTHER -> ItemUnit.ST
    }

/**
 * Receptet ifyllt från registret (REC-7): namn, styrka, form och formens enhet. Doseringen,
 * förpackningsstorleken och allt annat lämnas som det var.
 */
fun Regimen.fillFrom(entry: MedicineEntry): Regimen =
    copy(name = entry.name, strength = entry.strength, form = entry.form, unit = entry.form.defaultUnit)

/**
 * Läkemedelsverkets läkemedelsformer ("Filmdragerad tablett", "Kapsel, hård", "Ögondroppar,
 * lösning" …) som receptets [MedicineForm] (REC-7). Ordningen avgör: en injektion i pulverform är
 * Annat, en inhalationskapsel är Inhalator. Okänt blir Annat.
 */
object MedicineForms {
    private val rules: List<Pair<Regex, MedicineForm>> = listOf(
        "injektion|infusion|dialys|implantat|gas|instillation|spolvätska" to MedicineForm.OTHER,
        "inhal|nebulisator" to MedicineForm.INHALER,
        "plåster" to MedicineForm.PATCH,
        "droppar" to MedicineForm.DROPS,
        "spray|kräm|salva|gel|skum|pasta|suppositori|vagitori|stolpiller|tuggummi|schampo|lack|stift|tampong" to MedicineForm.OTHER,
        "kapsel" to MedicineForm.CAPSULE,
        "tablett|resoriblett" to MedicineForm.TABLET,
        "pulver|granulat" to MedicineForm.POWDER,
        "lösning|suspension|sirap|emulsion|vätska|dryck|mixtur" to MedicineForm.LIQUID,
    ).map { (pattern, form) -> Regex(pattern) to form }

    fun of(formText: String): MedicineForm {
        val text = formText.lowercase()
        return rules.firstOrNull { (pattern, _) -> pattern.containsMatchIn(text) }?.second ?: MedicineForm.OTHER
    }
}

/**
 * Vad namnfältet i ett nytt recept visar (REC-6, REC-7): [suggestions] medan man skriver,
 * [noMatch] när det skrivna inte finns i listan, och [filled] så länge fälten är som valet fyllde i.
 */
data class MedicineLookup(
    val suggestions: List<MedicineMatch> = emptyList(),
    val noMatch: Boolean = false,
    val filled: Boolean = false,
)

/**
 * Förslagen för [regimen] när [picked] är det senast valda läkemedlet (eller `null`). Efter ett val
 * visas inga förslag förrän namnet ändras; "ifyllt" gäller tills namn, styrka, form eller enhet
 * ändrats. En tom lista (filen saknas) ger aldrig "ingen träff".
 */
fun MedicineCatalog.lookup(regimen: Regimen, picked: MedicineEntry?): MedicineLookup {
    if (picked != null && regimen.name == picked.name) {
        val unchanged = regimen.strength == picked.strength && regimen.form == picked.form && regimen.unit == picked.form.defaultUnit
        return MedicineLookup(filled = unchanged)
    }
    val suggestions = search(regimen.name)
    val searched = MedicineCatalog.fold(regimen.name.trim()).length >= MedicineCatalog.MIN_QUERY
    return MedicineLookup(suggestions, noMatch = suggestions.isEmpty() && searched && entries.isNotEmpty())
}
