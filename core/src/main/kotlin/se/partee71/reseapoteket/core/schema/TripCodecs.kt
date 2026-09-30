package se.partee71.reseapoteket.core.schema

import se.partee71.reseapoteket.core.model.ItemUnit
import se.partee71.reseapoteket.core.model.PackingItem
import se.partee71.reseapoteket.core.model.PackingSection
import se.partee71.reseapoteket.core.model.PackingSource
import se.partee71.reseapoteket.core.model.Trip
import se.partee71.reseapoteket.core.model.TripStatus

object TripCodec : DocCodec<Trip> {
    override fun encode(value: Trip): Doc = mapOf(
        "name" to value.name,
        "startDate" to value.startDate.encodeDate(),
        "days" to value.days,
        "travellerIds" to value.travellerIds,
        "status" to value.status.encodeEnum(),
        "createdAt" to value.createdAt,
        "updatedAt" to value.updatedAt,
        "archived" to value.archived,
    )

    override fun decode(id: String, map: Doc) = Trip(
        id = id,
        name = map.string("name"),
        startDate = map.localDate("startDate"),
        days = map.int("days", default = 1),
        travellerIds = map.stringList("travellerIds"),
        status = map.enum("status", TripStatus.PLANNING),
        createdAt = map.instant("createdAt"),
        updatedAt = map.instant("updatedAt"),
        archived = map.bool("archived"),
    )
}

object PackingItemCodec : DocCodec<PackingItem> {
    override fun encode(value: PackingItem): Doc = mapOf(
        "section" to value.section.encodeEnum(),
        "personId" to value.personId,
        "sourceType" to value.sourceType.encodeEnum(),
        "sourceId" to value.sourceId,
        "name" to value.name,
        "unit" to value.unit.encodeEnum(),
        "suggestedQuantity" to value.suggestedQuantity,
        "quantity" to value.quantity,
        "packages" to value.packages,
        "reason" to value.reason,
        "checked" to value.checked,
        "checkedAt" to value.checkedAt,
        "hidden" to value.hidden,
        "sortOrder" to value.sortOrder,
    )

    override fun decode(id: String, map: Doc) = PackingItem(
        id = id,
        section = map.enum("section", PackingSection.MEDICINE),
        personId = map.stringOrNull("personId"),
        sourceType = map.enum("sourceType", PackingSource.MANUAL),
        sourceId = map.stringOrNull("sourceId"),
        name = map.string("name"),
        unit = map.enum("unit", ItemUnit.ST),
        suggestedQuantity = map.int("suggestedQuantity"),
        quantity = map.int("quantity"),
        packages = map.intOrNull("packages"),
        reason = map.string("reason"),
        checked = map.bool("checked"),
        checkedAt = map.instant("checkedAt"),
        hidden = map.bool("hidden"),
        sortOrder = map.int("sortOrder"),
    )
}
