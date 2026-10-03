package se.partee71.reseapoteket.core.model

import kotlinx.datetime.LocalTime

/** `households/{hid}/familyItems/{fid}` – krämer, plåster och annat på hushållsnivå. */
data class FamilyItem(
    override val id: String,
    val name: String = "",
    val category: FamilyItemCategory = FamilyItemCategory.OTHER,
    val unit: ItemUnit = ItemUnit.ST,
    // Saknad regel betyder "okänd", aldrig en gissad mängd; formuläret väljer en typ för nya artiklar.
    val rule: QuantityRule = QuantityRule.Unknown(),
    val notes: String = "",
    val active: Boolean = true,
    /** Packas på resedagen (PAK-6): samlas sist i packlistan; [travelDayTime] är en egen påminnelsetid. */
    val travelDay: Boolean = false,
    val travelDayTime: LocalTime? = null,
    override val sortOrder: Int = 0,
    override val archived: Boolean = false,
) : Identified, Sortable, Archivable

enum class FamilyItemCategory { CREAM, PLASTER, TOOL, OTHER }
