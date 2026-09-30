package se.partee71.reseapoteket.data.common

import se.partee71.reseapoteket.core.model.FamilyItem
import se.partee71.reseapoteket.core.model.Household
import se.partee71.reseapoteket.core.model.PackingItem
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.Regimen
import se.partee71.reseapoteket.core.model.Trip

/** Skapar alla samlingar – sökvägarna finns på ett ställe (`Paths`). */
interface CollectionFactory {
    fun households(): EntityCollection<Household>

    fun persons(): EntityCollection<Person>

    fun regimens(personId: String): EntityCollection<Regimen>

    fun familyItems(): EntityCollection<FamilyItem>

    fun trips(): EntityCollection<Trip>

    fun packingItems(tripId: String): EntityCollection<PackingItem>
}
