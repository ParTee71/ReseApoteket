package se.partee71.reseapoteket.core.schema

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun `nyare data än appen migreras aldrig och markeras som nyare`() {
        assertTrue(Schema.isNewerThanApp(Schema.CURRENT_VERSION + 1))
        assertFalse(Schema.isNewerThanApp(Schema.CURRENT_VERSION))
        assertFailsWith<IllegalArgumentException> {
            SchemaMigrator.migrate(Schema.CURRENT_VERSION + 1, "persons", doc)
        }
    }
}
