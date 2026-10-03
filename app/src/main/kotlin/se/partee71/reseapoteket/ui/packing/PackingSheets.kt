package se.partee71.reseapoteket.ui.packing

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.engine.PackingList
import se.partee71.reseapoteket.core.engine.UnitNames
import se.partee71.reseapoteket.core.model.PackingItem
import se.partee71.reseapoteket.core.model.PackingSection
import se.partee71.reseapoteket.core.model.PackingSource
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.ui.common.EditorUiState
import se.partee71.reseapoteket.ui.components.AppBottomSheet
import se.partee71.reseapoteket.ui.components.AppButton
import se.partee71.reseapoteket.ui.components.AppSegmentedChoice
import se.partee71.reseapoteket.ui.components.AppTextField
import se.partee71.reseapoteket.ui.components.ButtonVariant
import se.partee71.reseapoteket.ui.components.ChipRow
import se.partee71.reseapoteket.ui.components.LabeledGroup
import se.partee71.reseapoteket.ui.components.PackingPreview
import se.partee71.reseapoteket.ui.components.PersonChip
import se.partee71.reseapoteket.ui.components.QuantityStepper
import se.partee71.reseapoteket.ui.components.TravelDayField
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.reseapoteket.ui.theme.AppTypography

/** Ändra mängd (PAK-3): stegare, förslaget med förklaringen och "Återställ till förslag". */
@Composable
internal fun QuantitySheet(row: PackingRow, onEvent: (PackingListEvent) -> Unit, error: DataError? = null) {
    val item = row.item
    AppBottomSheet(item.name, onDismiss = { onEvent(PackingListEvent.CloseQuantity) }, error = error) {
        listOfNotNull(row.person?.name, row.schedule).takeIf { it.isNotEmpty() }?.let {
            Text(it.joinToString(" · "), style = AppTypography.itemSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        QuantityStepper(item.quantity, { onEvent(PackingListEvent.Quantity(it)) }, UnitNames.name(item.quantity, item.unit))
        if (item.sourceType != PackingSource.MANUAL) {
            val suggested = item.copy(quantity = item.suggestedQuantity)
            PackingPreview(stringResource(R.string.packing_suggestion), PackingList.amount(suggested), reason = item.reason.ifBlank { null })
        }
        if (PackingList.isChanged(item)) {
            AppButton(stringResource(R.string.packing_reset_format, item.suggestedQuantity), { onEvent(PackingListEvent.ResetQuantity) }, variant = ButtonVariant.Text)
        }
        AppButton(stringResource(R.string.packing_done), { onEvent(PackingListEvent.CloseQuantity) }, Modifier.fillMaxWidth())
    }
}

/** Lägg till en egen rad (PAK-4): vad, var, vem (för mediciner och kosttillskott) och hur många. */
@Composable
internal fun AddItemSheet(state: EditorUiState<PackingItem>, travellers: List<Person>, onEvent: (PackingListEvent) -> Unit, error: DataError? = null) {
    val item = state.value
    AppBottomSheet(stringResource(R.string.packing_add_title), onDismiss = { onEvent(PackingListEvent.CloseAdd) }, error = error) {
        AppTextField(
            item.name,
            { onEvent(PackingListEvent.AddName(it)) },
            stringResource(R.string.packing_what),
            error = state.errorFor(ManualItemValidator.NAME)?.let { stringResource(it) },
        )
        LabeledGroup(stringResource(R.string.packing_where)) {
            AppSegmentedChoice(
                listOf(stringResource(R.string.packing_medicines), stringResource(R.string.packing_supplements), stringResource(R.string.packing_other)),
                selectedIndex = item.section.ordinal,
                onSelect = { onEvent(PackingListEvent.AddSection(PackingSection.entries[it])) },
            )
        }
        if (item.section != PackingSection.FAMILY) {
            LabeledGroup(stringResource(R.string.packing_who), error = state.errorFor(ManualItemValidator.PERSON)?.let { stringResource(it) }) {
                ChipRow {
                    travellers.forEach { person ->
                        PersonChip(person.name, person.emoji, AppColors.person(person.colorHex), person.id == item.personId, { onEvent(PackingListEvent.AddPerson(person.id)) })
                    }
                }
            }
        }
        LabeledGroup(stringResource(R.string.packing_how_many)) {
            QuantityStepper(item.quantity, { onEvent(PackingListEvent.AddQuantity(it)) }, UnitNames.name(item.quantity, item.unit), range = ManualItemValidator.QUANTITY_RANGE)
        }
        TravelDayField(item.travelDay, item.travelDayTime, { on, time -> onEvent(PackingListEvent.AddTravelDay(on, time)) })
        AppButton(stringResource(R.string.add), { onEvent(PackingListEvent.AddSave) }, Modifier.fillMaxWidth(), enabled = state.canSave, loading = state.saving)
    }
}
