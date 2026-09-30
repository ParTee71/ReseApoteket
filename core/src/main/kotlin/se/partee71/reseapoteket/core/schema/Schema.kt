package se.partee71.reseapoteket.core.schema

/**
 * Dataformatets version (`households/{hid}.schemaVersion`, skill data-safety-backup).
 * Höjs vid betydelseändring: omdöpt fält, ändrad enhet eller struktur, eller ett nytt
 * enum-värde/en ny variant som en äldre app inte kan tolka. Ett nytt valfritt fält med
 * default höjer den inte.
 */
object Schema {
    const val CURRENT_VERSION = 1

    /** Version för ett hushåll utan `schemaVersion` – det första formatet, aldrig "senaste". */
    const val FIRST_VERSION = 1

    /** Nyare data än appen förstår: läs, men skriv ingenting och be om uppdatering. */
    fun isNewerThanApp(version: Int): Boolean = version > CURRENT_VERSION
}
