package se.partee71.reseapoteket.data.firestore

import se.partee71.reseapoteket.core.model.Identified
import se.partee71.reseapoteket.core.schema.DocCodec
import se.partee71.reseapoteket.core.schema.FamilyItemCodec
import se.partee71.reseapoteket.core.schema.HouseholdCodec
import se.partee71.reseapoteket.core.schema.PackingItemCodec
import se.partee71.reseapoteket.core.schema.PersonCodec
import se.partee71.reseapoteket.core.schema.RegimenCodec
import se.partee71.reseapoteket.core.schema.TripCodec
import se.partee71.reseapoteket.data.common.CollectionFactory
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.EntityCollection

/**
 * Varje samlings namn, codec och sökväg – ett ställe, delat av den riktiga fabriken och
 * testernas `FakeCollectionFactory`, så att de bevisligen använder samma tabell.
 */
abstract class CollectionTable : CollectionFactory {

    /** Skapar en samling; [path] kastar [DataError.NoHousehold] om den kräver ett hushåll. */
    protected abstract fun <T : Identified> create(
        codec: DocCodec<T>,
        name: String,
        path: (householdId: String?) -> String,
    ): EntityCollection<T>

    override fun households() = create(HouseholdCodec, Paths.HOUSEHOLDS) { Paths.HOUSEHOLDS }

    override fun persons() = create(PersonCodec, Paths.PERSONS) { Paths.persons(required(it)) }

    override fun regimens(personId: String) =
        create(RegimenCodec, Paths.REGIMENS) { Paths.regimens(required(it), personId) }

    override fun familyItems() = create(FamilyItemCodec, Paths.FAMILY_ITEMS) { Paths.familyItems(required(it)) }

    override fun trips() = create(TripCodec, Paths.TRIPS) { Paths.trips(required(it)) }

    override fun packingItems(tripId: String) =
        create(PackingItemCodec, Paths.PACKING_ITEMS) { Paths.packingItems(required(it), tripId) }

    private fun required(householdId: String?): String = householdId ?: throw DataError.NoHousehold
}
