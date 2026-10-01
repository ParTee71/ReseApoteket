package se.partee71.reseapoteket.core.schema

/**
 * Lyfter ett dokument i ett äldre format steg för steg till [Schema.CURRENT_VERSION].
 *
 * Versionen hör till hushållet, inte till varje dokument. Därför:
 * - Varje steg ska vara **idempotent** (känna igen redan migrerad data), eftersom samma
 *   dokument kan passera flera gånger innan hushållet stämplats om.
 * - Hushållets `schemaVersion` höjs **bara** av en fullständig migrering av alla hushållets
 *   dokument med `tools/db/migrate.mjs` (appen höjer den aldrig själv), aldrig av en vanlig
 *   skrivning – `HouseholdCodec` skriver tillbaka den version den läste.
 * Varje steg speglas i `tools/db/migrate.mjs` och testas i `SchemaMigratorTest`.
 */
object SchemaMigrator {

    /** Ett steg från version `n` till `n + 1` för ett dokument i [collection]. */
    fun interface Step {
        fun migrate(collection: String, doc: Doc): Doc
    }

    /** Nyckel = versionen steget migrerar *från*. */
    internal val steps: Map<Int, Step> = mapOf(
        // 1 → 2: bara ett nytt enum-värde (POWDER) – inga befintliga dokument ändras.
        1 to Step { _, doc -> doc },
    )

    fun migrate(from: Int, collection: String, doc: Doc): Doc {
        require(!Schema.isNewerThanApp(from)) {
            "schemaVersion $from är nyare än appens ${Schema.CURRENT_VERSION} – kan inte migreras bakåt"
        }
        // En version under den första (trasigt värde) är det första formatet.
        return (from.coerceAtLeast(Schema.FIRST_VERSION) until Schema.CURRENT_VERSION).fold(doc) { current, version ->
            val step = checkNotNull(steps[version]) { "Migreringssteg saknas för version $version" }
            step.migrate(collection, current)
        }
    }
}
