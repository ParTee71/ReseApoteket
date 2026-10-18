package se.partee71.reseapoteket.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import se.partee71.reseapoteket.core.engine.PastedStatus.ADD
import se.partee71.reseapoteket.core.engine.PastedStatus.EXISTS
import se.partee71.reseapoteket.core.engine.PastedStatus.TOO_LONG

/** En inklistrad lista blir rader under Övrigt (PAK-7). */
class PastedListTest {

    private fun parse(vararg lines: String, existing: List<String> = emptyList()) = PastedList.parse(lines.joinToString("\n"), existing).map { Triple(it.name, it.quantity, it.status) }

    @Test
    fun `ett tal sist blir antalet, annars ett`() {
        assertEquals(
            listOf(
                Triple("Solkräm", 2, ADD),
                Triple("Solglasögon", 2, ADD),
                Triple("Badlakan", 3, ADD),
                Triple("Strumpor", 10, ADD),
                Triple("Laddare", 1, ADD),
                Triple("Alvedon 500 mg", 1, ADD)
            ),
            parse("Solkräm 2", "Solglasögon x2", "Badlakan 3 st", "Strumpor ×10 stycken", "Laddare", "Alvedon 500 mg")
        )
    }

    @Test
    fun `ett tal utanför 1–999, eller utan namn före, hör till namnet`() {
        assertEquals(
            listOf(Triple("Batterier 1000", 1, ADD), Triple("Strumpor 0", 1, ADD), Triple("2", 1, ADD), Triple("Kabel2", 1, ADD)),
            parse("Batterier 1000", "Strumpor 0", "2", "Kabel2")
        )
    }

    @Test
    fun `punkter och bockar i början tas bort, men inte ett decimaltal`() {
        assertEquals(
            listOf("Solkräm", "Badkläder", "Laddare", "Powerbank", "Myggmedel", "Plåster", "Keps", "Hatt", "3.5 mm-kabel"),
            parse("- Solkräm", "• Badkläder", "* Laddare", "☐ Powerbank", "[ ] Myggmedel", "[x] Plåster", "1. Keps", "2) Hatt", "3.5 mm-kabel").map { it.first }
        )
    }

    @Test
    fun `tomma rader och dubbletter hoppas över – den första gäller`() {
        assertEquals(
            listOf(Triple("Solkräm", 2, ADD), Triple("Keps", 1, ADD)),
            parse("", "  Solkräm 2  ", "   ", "solkräm", "- SOLKRÄM 3", "Keps", "\t")
        )
    }

    @Test
    fun `det som redan finns och för långa rader visas men läggs inte till`() {
        val long = "a".repeat(201)
        assertEquals(
            listOf(Triple("plåster", 2, EXISTS), Triple(long, 1, TOO_LONG), Triple("a".repeat(200), 1, ADD)),
            parse("plåster 2", long, "a".repeat(200), existing = listOf(" Plåster "))
        )
    }

    @Test
    fun `en tom text ger ingenting`() {
        assertEquals(emptyList(), PastedList.parse(""))
        assertEquals(emptyList(), PastedList.parse("\n\n - \n"))
    }
}
