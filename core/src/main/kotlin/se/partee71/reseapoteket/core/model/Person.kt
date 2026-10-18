package se.partee71.reseapoteket.core.model

/** `households/{hid}/persons/{pid}` – en person i hushållet. */
data class Person(
    override val id: String,
    val name: String = "",
    val emoji: String = "",
    val colorHex: String = "",
    override val sortOrder: Int = 0,
    override val archived: Boolean = false
) : Identified,
    Sortable,
    Archivable
