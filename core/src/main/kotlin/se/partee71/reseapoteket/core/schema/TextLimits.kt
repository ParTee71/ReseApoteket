package se.partee71.reseapoteket.core.schema

/**
 * Längsta text som sparas (NFR-16): samma tak i `firestore.rules` (`maxShort`/`maxLong`, hålls
 * lika av tools/db/test/schema.test.mjs) och i textfälten, så att det man skriver alltid går att
 * spara. Generöst tilltagna – de skyddar hushållets kvot, inte formuläret.
 */
object TextLimits {
    /** Namn, styrka och andra enradiga fält. */
    const val SHORT = 200

    /** Anteckningar och förklaringar. */
    const val LONG = 2000
}
