@file:OptIn(ExperimentalMaterial3Api::class)

package se.partee71.reseapoteket.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.datetime.LocalDate
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.ui.common.DateFormat
import se.partee71.reseapoteket.ui.theme.AppColors

/**
 * Datumfält: visar datumet på svenska ("lör 19 dec 2026") och öppnar datumväljaren vid tryck.
 * TalkBack läser fältet som en knapp med etikett, datum och eventuellt fel.
 */
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val pickLabel = stringResource(R.string.pick_date)
    val shown = date?.let(DateFormat::display).orEmpty()
    val description = listOf(label, shown.ifEmpty { pickLabel }, error.orEmpty()).filter { it.isNotEmpty() }.joinToString(", ")
    Box(modifier) {
        // Fältet visar bara värdet; ytan ovanpå är det enda som tar emot tryck och fokus.
        AppTextField(
            value = shown,
            onValueChange = {},
            label = label,
            modifier = Modifier.focusProperties { canFocus = false }.clearAndSetSemantics {},
            error = error,
            readOnly = true,
            trailingIcon = R.drawable.ic_calendar,
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(role = Role.Button, onClickLabel = pickLabel) { picking = true }
                .semantics { contentDescription = description },
        )
    }
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date?.let(DateFormat::toEpochMillis))
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                AppButton(stringResource(R.string.ok), onClick = {
                    state.selectedDateMillis?.let { onDateChange(DateFormat.fromEpochMillis(it)) }
                    picking = false
                }, variant = ButtonVariant.Text)
            },
            dismissButton = { AppButton(stringResource(R.string.cancel), { picking = false }, variant = ButtonVariant.Text) },
            colors = DatePickerDefaults.colors(containerColor = AppColors.extended.card),
        ) {
            DatePicker(state)
        }
    }
}
