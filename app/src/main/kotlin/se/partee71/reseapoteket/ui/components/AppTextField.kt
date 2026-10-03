package se.partee71.reseapoteket.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.reseapoteket.ui.theme.AppShapes
import se.partee71.reseapoteket.ui.theme.AppTypography

/**
 * Det enda textfältet: etikett, och under fältet antingen [error] (röd, läses av TalkBack som
 * fel) eller [helper]. Fält i formulär får sina fel från `EditorState`. Tangentbordet börjar
 * med stor bokstav (NFR-18); ett fält som anger egna [keyboardOptions], t.ex. siffror, styr själv.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    helper: String? = null,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    @DrawableRes trailingIcon: Int? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
) {
    val card = AppColors.extended.card
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = AppTypography.body,
        label = { Text(label) },
        trailingIcon = trailingIcon?.let { { Icon(painterResource(it), contentDescription = null) } },
        supportingText = (error ?: helper)?.let { { Text(it, style = AppTypography.itemSubtitle) } },
        isError = error != null,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        shape = AppShapes.field,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = card,
            unfocusedContainerColor = card,
            disabledContainerColor = card,
            errorContainerColor = card,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}
