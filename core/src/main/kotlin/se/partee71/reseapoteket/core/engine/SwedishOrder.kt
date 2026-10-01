package se.partee71.reseapoteket.core.engine

import java.text.Collator
import java.util.Locale

/** Svensk sorteringsordning – den enda i appen (packförslaget, reselistan). */
object SwedishOrder {
    /** Namn sorteras i svensk ordning: … x, y, z, å, ä, ö – inte på teckenkod. */
    val names: Comparator<String> = Collator.getInstance(Locale.forLanguageTag("sv-SE")).let { collator ->
        Comparator { a, b -> collator.compare(a, b) }
    }
}
