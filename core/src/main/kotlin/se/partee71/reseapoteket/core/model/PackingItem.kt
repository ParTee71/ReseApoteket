package se.partee71.reseapoteket.core.model

import kotlin.time.Instant

/** `households/{hid}/trips/{tid}/packingItems/{iid}` – en rad i packlistan. */
data class PackingItem(
    override val id: String,
    val section: PackingSection = PackingSection.MEDICINE,
    val personId: String? = null,
    val sourceType: PackingSource = PackingSource.MANUAL,
    val sourceId: String? = null,
    val name: String = "",
    val unit: ItemUnit = ItemUnit.ST,
    val suggestedQuantity: Int = 0,
    val quantity: Int = 0,
    val packages: Int? = null,
    val reason: String = "",
    val checked: Boolean = false,
    val checkedAt: Instant? = null,
    val hidden: Boolean = false,
    override val sortOrder: Int = 0,
) : Identified, Sortable

enum class PackingSection { MEDICINE, SUPPLEMENT, FAMILY }

enum class PackingSource { REGIMEN, FAMILY_ITEM, MANUAL }
