package se.partee71.reseapoteket.core.schema

import se.partee71.reseapoteket.core.model.ItemUnit
import se.partee71.reseapoteket.core.model.PackingItem
import se.partee71.reseapoteket.core.model.PackingSection
import se.partee71.reseapoteket.core.model.PackingSource
import se.partee71.reseapoteket.core.model.Trip
import se.partee71.reseapoteket.core.model.TripStatus

object TripCodec : DocCodec<Trip> {
    /** Fältet som packlistan skriver när resans status följer listan (PAK-5). */
    const val STATUS = "status"

    /** Fälten som reseformuläret ändrar – inte status, som följer packlistan (PAK-5). */
    val FORM_FIELDS = setOf("name", "startDate", "days", "travellerIds")

    override fun encode(value: Trip): Doc = mapOf(
        "name" to value.name,
        "startDate" to value.startDate.encodeDate(),
        "days" to value.days,
        "travellerIds" to value.travellerIds,
        STATUS to value.status.encodeEnum(),
        "createdAt" to value.createdAt,
        "updatedAt" to value.updatedAt,
        "archived" to value.archived
    )

    override fun decode(id: String, map: Doc) = Trip(
        id = id,
        name = map.string("name"),
        startDate = map.localDate("startDate"),
        days = map.int("days", default = 1),
        travellerIds = map.stringList("travellerIds"),
        status = map.enum(STATUS, TripStatus.PLANNING),
        createdAt = map.instant("createdAt"),
        updatedAt = map.instant("updatedAt"),
        archived = map.bool("archived")
    )
}

object PackingItemCodec : DocCodec<PackingItem> {
    // Fälten som packlistan skriver var för sig (PAK-2–4), så att en samtidig ändring av en annan
    // del av raden inte skrivs över.
    const val QUANTITY = "quantity"
    const val CHECKED = "checked"
    const val CHECKED_AT = "checkedAt"
    const val HIDDEN = "hidden"

    override fun encode(value: PackingItem): Doc = mapOf(
        "section" to value.section.encodeEnum(),
        "personId" to value.personId,
        "sourceType" to value.sourceType.encodeEnum(),
        "sourceId" to value.sourceId,
        "name" to value.name,
        "unit" to value.unit.encodeEnum(),
        "suggestedQuantity" to value.suggestedQuantity,
        QUANTITY to value.quantity,
        "packages" to value.packages,
        "reason" to value.reason,
        CHECKED to value.checked,
        CHECKED_AT to value.checkedAt,
        HIDDEN to value.hidden,
        TRAVEL_DAY to value.travelDay,
        TRAVEL_DAY_TIME to value.travelDayTime.encodeTime(),
        "sortOrder" to value.sortOrder
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
        quantity = map.int(QUANTITY),
        packages = map.intOrNull("packages"),
        reason = map.string("reason"),
        checked = map.bool(CHECKED),
        checkedAt = map.instant(CHECKED_AT),
        hidden = map.bool(HIDDEN),
        travelDay = map.bool(TRAVEL_DAY),
        travelDayTime = map.localTime(TRAVEL_DAY_TIME),
        sortOrder = map.int("sortOrder")
    )
}
