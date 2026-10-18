package se.partee71.reseapoteket.core.schema

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class SchemaMigratorTest {

    private val doc: Doc = mapOf("name" to "Anna", "okänt" to listOf(1, 2))

    @Test
    fun `nuvarande version migreras till sig själv`() {
        assertEquals(doc, SchemaMigrator.migrate(Schema.CURRENT_VERSION, "persons", doc))
    }

    @Test
    fun `varje version under den nuvarande har ett steg`() {
        for (version in 1 until Schema.CURRENT_VERSION) {
            assertTrue(version in SchemaMigrator.steps, "migreringssteg saknas för version $version")
        }
    }

    @Test
    fun `1 till 2 lämnar dokumenten orörda (bara formen POWDER tillkom)`() {
        val regimen: Doc = mapOf("name" to "Levaxin", "form" to "TABLET", "okänt" to 1)
        assertEquals(regimen, SchemaMigrator.migrate(1, "regimens", regimen))
        assertEquals(doc, SchemaMigrator.migrate(1, "persons", doc))
    }

    @Test
    fun `ett hushåll stämplas av appen bara när inget steg dit ändrar data (BCK-5)`() {
        assertTrue(SchemaMigrator.canStamp(1), "1 → 2 lägger bara till POWDER")
        assertTrue(SchemaMigrator.canStamp(0), "ett trasigt värde är det första formatet")
        assertFalse(SchemaMigrator.canStamp(Schema.CURRENT_VERSION))
        assertFalse(SchemaMigrator.canStamp(Schema.CURRENT_VERSION + 1))
    }

    @Test
    fun `nyare data än appen migreras aldrig och markeras som nyare`() {
        assertTrue(Schema.isNewerThanApp(Schema.CURRENT_VERSION + 1))
        assertFalse(Schema.isNewerThanApp(Schema.CURRENT_VERSION))
        assertFailsWith<IllegalArgumentException> {
            SchemaMigrator.migrate(Schema.CURRENT_VERSION + 1, "persons", doc)
        }
    }

    @Test
    fun `en trasig version under den första läses som det första formatet`() {
        assertEquals(Schema.FIRST_VERSION, Schema.versionOf(0L))
        assertEquals(Schema.FIRST_VERSION, Schema.versionOf(-3))
        assertEquals(Schema.FIRST_VERSION, Schema.versionOf(null))
        assertEquals(Schema.FIRST_VERSION, Schema.versionOf("1"))
        assertEquals(Schema.CURRENT_VERSION, Schema.versionOf(Schema.CURRENT_VERSION.toLong()))
        assertEquals(Int.MAX_VALUE, Schema.versionOf(Long.MAX_VALUE), "för stort är nyare än appen, inte ett litet tal")
        assertEquals(doc, SchemaMigrator.migrate(0, "persons", doc))
        assertEquals(Schema.FIRST_VERSION, HouseholdCodec.decode("h", mapOf("schemaVersion" to 0L)).schemaVersion)
    }
}
