package se.partee71.reseapoteket.core.schema

/**
 * Lyfter ett dokument i ett äldre format steg för steg till [Schema.CURRENT_VERSION].
 *
 * Versionen hör till hushållet, inte till varje dokument. Därför:
 * - Varje steg ska vara **idempotent** (känna igen redan migrerad data), eftersom samma
 *   dokument kan passera flera gånger innan hushållet stämplats om.
 * - Hushållets `schemaVersion` höjs **bara** av en fullständig migrering av alla hushållets
 *   dokument (appen vid inloggning eller `tools/db/migrate.mjs`), aldrig av en vanlig
 *   skrivning – `HouseholdCodec` skriver tillbaka den version den läste.
 * Varje steg speglas i `tools/db/migrate.mjs` och testas i `SchemaMigratorTest`.
 */
object SchemaMigrator {

    /** Ett steg från version `n` till `n + 1` för ett dokument i [collection]. */
    fun interface Step {
        fun migrate(collection: String, doc: Doc): Doc
    }

    /** Nyckel = versionen steget migrerar *från*. Tom tills formatet ändras första gången. */
    internal val steps: Map<Int, Step> = emptyMap()

    fun migrate(from: Int, collection: String, doc: Doc): Doc {
        require(!Schema.isNewerThanApp(from)) {
            "schemaVersion $from är nyare än appens ${Schema.CURRENT_VERSION} – kan inte migreras bakåt"
        }
        return (from until Schema.CURRENT_VERSION).fold(doc) { current, version ->
            val step = checkNotNull(steps[version]) { "Migreringssteg saknas för version $version" }
            step.migrate(collection, current)
        }
    }
}
