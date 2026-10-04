package se.partee71.reseapoteket.reminders

import android.content.Context
import android.content.Intent
import se.partee71.reseapoteket.MainActivity
import se.partee71.reseapoteket.core.schema.DocumentIds

/**
 * Länken från en påminnelse till resans packlista (PÅM-7). Notisen öppnar appen via ett
 * icke-exporterat alias av [MainActivity] (`OpenTripFromReminder` i manifestet): bara då läses
 * resan, så en annan app som startar [MainActivity] med samma extra öppnar ingenting.
 */
object TripLink {
    const val EXTRA_TRIP_ID = "se.partee71.reseapoteket.TRIP_ID"

    /** Aliasets klassnamn – i appens kodpaket, oberoende av applicationId (debug-suffix). */
    val ALIAS: String = "${MainActivity::class.java.name.substringBeforeLast('.')}.OpenTripFromReminder"

    fun intent(context: Context, tripId: String): Intent =
        Intent().setClassName(context, ALIAS).putExtra(EXTRA_TRIP_ID, tripId)

    /**
     * Resan i [intent], eller `null` om den inte kom via notisens alias, saknas eller inte är ett
     * giltigt ID. Okända extras från en annan app (som inte går att packa upp på Android 11–12)
     * blir också `null` i stället för en krasch.
     */
    fun tripId(intent: Intent): String? = try {
        if (intent.component?.className != ALIAS) null else intent.getStringExtra(EXTRA_TRIP_ID)?.takeIf(DocumentIds::isValid)
    } catch (_: RuntimeException) {
        null
    }

    /** Tar bort resan ur [intent], så att den inte öppnas igen; tål samma främmande extras som [tripId]. */
    fun consume(intent: Intent) {
        try {
            intent.removeExtra(EXTRA_TRIP_ID)
        } catch (_: RuntimeException) {
            // Kunde inte packas upp – då finns heller ingen resa att ta bort.
        }
    }
}
