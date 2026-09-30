package se.partee71.reseapoteket.core.model

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
    override val sortOrder: Int = 0,
    override val archived: Boolean = false,
) : Identified, Sortable, Archivable

enum class FamilyItemCategory { CREAM, PLASTER, TOOL, OTHER }
