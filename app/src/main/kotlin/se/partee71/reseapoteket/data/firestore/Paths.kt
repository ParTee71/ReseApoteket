package se.partee71.reseapoteket.data.firestore

/**
 * Alla Firestore-sökvägar – enda stället i appen. Speglas i `tools/db/lib/collections.mjs`;
 * `tools/db/test/collections.test.mjs` håller listorna lika (skill data-safety-backup).
 */
object Paths {
    const val HOUSEHOLDS = "households"
    const val PERSONS = "persons"
    const val REGIMENS = "regimens"
    const val FAMILY_ITEMS = "familyItems"
    const val TRIPS = "trips"
    const val PACKING_ITEMS = "packingItems"

    fun household(hid: String) = "households/$hid"

    fun persons(hid: String) = "households/$hid/persons"

    fun regimens(hid: String, personId: String) = "households/$hid/persons/$personId/regimens"

    fun familyItems(hid: String) = "households/$hid/familyItems"

    fun trips(hid: String) = "households/$hid/trips"

    fun packingItems(hid: String, tripId: String) = "households/$hid/trips/$tripId/packingItems"
}
