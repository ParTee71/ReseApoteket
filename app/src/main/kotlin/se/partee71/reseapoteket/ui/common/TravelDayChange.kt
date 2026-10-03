package se.partee71.reseapoteket.ui.common

import kotlinx.datetime.LocalTime

/**
 * Ett ändrat "Packas på resedagen" (PAK-6) från `TravelDayField` – samma händelse i recept,
 * familjeartiklar och egna rader. Avstängt har ingen egen tid ([ownTime]).
 */
data class TravelDayChange(val on: Boolean, val time: LocalTime?) {
    val ownTime: LocalTime? get() = time.takeIf { on }
}
