package se.partee71.reseapoteket.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import se.partee71.reseapoteket.core.model.RegimenKind
import se.partee71.reseapoteket.ui.familyitems.FamilyItemExample

/** Alla skärmar i appen (NAV-1–3). Sparas med kotlinx.serialization, så nycklarna överlever processdöd. */
@Serializable
sealed interface AppKey : NavKey

/** En flik i verktygsraden – roten i en egen back stack. */
@Serializable
sealed interface TopLevelKey : AppKey

@Serializable
data object TripsKey : TopLevelKey

@Serializable
data object PersonsKey : TopLevelKey

@Serializable
data object FamilyItemsKey : TopLevelKey

@Serializable
data object SettingsKey : TopLevelKey

@Serializable
data class PersonDetailKey(val personId: String) : AppKey

@Serializable
data class PersonEditKey(val personId: String? = null) : AppKey

/** Recept eller kosttillskott; [regimenId] = `null` för ett nytt av sorten [kind]. */
@Serializable
data class RegimenEditKey(val personId: String, val kind: RegimenKind, val regimenId: String? = null) : AppKey

/** En familjeartikel; [itemId] = `null` för en ny, som kan börja från ett [example] (FAM-4). */
@Serializable
data class FamilyItemEditKey(val itemId: String? = null, val example: FamilyItemExample? = null) : AppKey

@Serializable
data class TripEditKey(val tripId: String? = null) : AppKey

@Serializable
data class PackingListKey(val tripId: String) : AppKey

/** Det aktiva hushållets namn – från Inställningar (SET-2). */
@Serializable
data object HouseholdEditKey : AppKey

/** Komponentgalleriet – nås bara från Inställningar i debug-bygget. */
@Serializable
data object ComponentGalleryKey : AppKey

/** Flikarna i verktygsradens ordning; appen startar på den första (NAV-2). */
val TOP_LEVEL: List<TopLevelKey> = listOf(TripsKey, PersonsKey, FamilyItemsKey, SettingsKey)
