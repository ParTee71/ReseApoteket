package se.partee71.reseapoteket.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.model.DoseSchedule
import se.partee71.reseapoteket.core.model.QuantityRule
import se.partee71.reseapoteket.testing.captureLightAndDark
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.reseapoteket.ui.theme.Spacing
import se.partee71.reseapoteket.ui.theme.Tone

/**
 * Skärmdumpar (ljust + mörkt) av katalogens komponenter i vila – en bild per komponent med
 * dess viktigaste lägen. Popup-komponenter och ramar fotograferas i sina egna tester.
 */
@RunWith(RobolectricTestRunner::class)
class CatalogScreenshotTest {

    @Composable
    private fun Sheet(content: @Composable ColumnScope.() -> Unit) {
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            content = content,
        )
    }

    private val tablets: (Int) -> String = { if (it == 1) "tablett" else "tabletter" }

    @Test
    fun `knappar och meny`() {
        captureLightAndDark("AppButton_destruktiv_och_kompakt") {
            Sheet {
                AppButton("Radera", {}, variant = ButtonVariant.Destructive)
                AppButton("Spara", {}, compact = true)
            }
        }
        captureLightAndDark("AppIconButton_varianter") {
            Sheet {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    AppIconButton(R.drawable.ic_arrow_back, "Tillbaka", {}, variant = IconButtonVariant.Tonal)
                    AppIconButton(R.drawable.ic_more_vert, "Fler val", {})
                    AppIconButton(R.drawable.ic_add, "Lägg till", {}, variant = IconButtonVariant.Tonal, enabled = false)
                }
            }
        }
        captureLightAndDark("AddSplitButton_varianter") {
            Sheet {
                AddSplitButton("Lägg till", {}, menuItems = listOf(AppMenuItem("Räkna om", {})))
                AddSplitButton("Lägg till person", {})
            }
        }
    }

    @Test
    fun `kort, rader och rubriker`() {
        captureLightAndDark("ApotekCard_rader") {
            Sheet {
                ApotekCard {
                    SectionHeader("Mediciner", icon = R.drawable.ic_medication, count = "4 / 5")
                    ItemRow("Enalapril 10 mg", subtitle = "18 tabletter · 2 kartor")
                    AppDivider()
                    ItemRow("Levaxin 50 µg", subtitle = "9 tabletter · 1 karta")
                }
            }
        }
        captureLightAndDark("ItemRow_varianter") {
            Sheet {
                ApotekCard {
                    ItemRow("Anna", subtitle = "3 recept · 1 kosttillskott", leading = { PersonAvatar("🦊", AppColors.person(0)) }, navigates = true, onClick = {})
                    ItemRow("Solkräm", subtitle = "1 tub per 7 dagar", trailing = { InfoPill("ändrad") })
                    ItemRow("Erik", accent = AppColors.person(1), tinted = true)
                    ItemRow("Febertermometer", done = true)
                }
            }
        }
        captureLightAndDark("CheckRow_lagen") {
            Sheet {
                ApotekCard {
                    CheckRow("Enalapril 10 mg", false, {}, subtitle = "18 tabletter · 2 kartor", trailing = { InfoPill("1/dag", tone = Tone.Blue) })
                    CheckRow("Levaxin 50 µg", true, {}, subtitle = "9 tabletter · 1 karta")
                }
            }
        }
        captureLightAndDark("SwitchRow_lagen") {
            Sheet {
                ApotekCard {
                    SwitchRow("Flytta avprickade sist", true, {}, subtitle = "I packlistan")
                    SwitchRow("Visa arkiverade", false, {})
                }
            }
        }
        captureLightAndDark("SectionHeader_toner") {
            Sheet {
                SectionHeader("Mediciner", icon = R.drawable.ic_medication, count = "4 / 5")
                SectionHeader("Kosttillskott", icon = R.drawable.ic_eco, count = "2", tone = Tone.Green)
                SectionHeader("Övrigt", count = "3", tone = Tone.Blue)
            }
        }
        captureLightAndDark("InfoPill_toner") {
            Sheet { Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) { InfoPill("Mediciner"); InfoPill("Kosttillskott", tone = Tone.Green); InfoPill("Övrigt", tone = Tone.Blue) } }
        }
        captureLightAndDark("PersonAvatar_farger") {
            Sheet {
                ChipRow {
                    listOf("🦊", "🐻", "🐰", "🦉", "🐯", "🐢", "🦄", "🐧").forEachIndexed { i, e -> PersonAvatar(e, AppColors.person(i)) }
                    PersonAvatar("🦊", AppColors.person(0), size = AvatarSize.Small)
                }
            }
        }
        captureLightAndDark("PersonAvatar_stor") { Sheet { PersonAvatar("🦉", AppColors.person(3), size = AvatarSize.Large) } }
        captureLightAndDark("GroupLabel_over_kort") { Sheet { GroupLabel("Utseende"); ApotekCard { ItemRow("Tema") } } }
        captureLightAndDark("AppDivider_i_kort") { Sheet { ApotekCard { ItemRow("Anna"); AppDivider(); ItemRow("Erik") } } }
    }

    @Test
    fun `fält och val`() {
        captureLightAndDark("AppTextField_lagen") {
            Sheet {
                AppTextField("Solkräm", {}, "Namn")
                AppTextField("Faktor 50 till barnen", {}, "Anteckning", helper = "Visas i packlistan")
                AppTextField("", {}, "Namn", error = "Ange ett namn")
            }
        }
        captureLightAndDark("DateField_lagen") {
            Sheet {
                DateField("Startdatum", LocalDate(2026, 12, 19), {})
                DateField("Startdatum", null, {}, error = "Välj ett startdatum")
            }
        }
        captureLightAndDark("AppFilterChip_val") {
            Sheet {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    AppFilterChip("Tablett", true, {})
                    AppFilterChip("Kapsel", false, {})
                    AppFilterChip("Flytande", false, {}, enabled = false)
                }
            }
        }
        captureLightAndDark("PersonChip_val") {
            Sheet {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    PersonChip("Anna", "🦊", AppColors.person(0), true, {})
                    PersonChip("Lova", "🐰", AppColors.person(2), false, {})
                }
            }
        }
        captureLightAndDark("ChipRow_varianter") {
            Sheet {
                ChipRow { listOf("Krämer & salvor", "Plåster & förband", "Verktyg", "Övrigt").forEachIndexed { i, c -> AppFilterChip(c, i == 0, {}) } }
                ChipRow(wrap = false) { listOf("Anna" to "🦊", "Erik" to "🐻", "Lova" to "🐰", "Ture" to "🦉").forEachIndexed { i, (n, e) -> PersonChip(n, e, AppColors.person(i), i < 2, {}) } }
            }
        }
        captureLightAndDark("EmojiPicker_val") { Sheet { EmojiPicker("🦉", {}) } }
        captureLightAndDark("ColorSwatchPicker_val") { Sheet { ColorSwatchPicker(AppColors.PERSON_HEX[3], {}) } }
        captureLightAndDark("AppSegmentedChoice_tema") { Sheet { AppSegmentedChoice(listOf("Ljust", "Mörkt", "System"), 2, {}) } }
        captureLightAndDark("QuantityStepper_varde") {
            Sheet {
                QuantityStepper(7, {}, "dagar")
                QuantityStepper(0, {}, "tabletter", range = 0..9)
            }
        }
    }

    @Test
    fun `mängder`() {
        captureLightAndDark("DoseScheduleRow_dagligen") { Sheet { DoseScheduleRow(DoseSchedule.Daily(2, 1), {}, tablets) } }
        captureLightAndDark("DoseScheduleRow_veckodagar") {
            Sheet { DoseScheduleRow(DoseSchedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), 2), {}, tablets) }
        }
        captureLightAndDark("DoseScheduleRow_intervall_och_vid_behov") {
            Sheet {
                DoseScheduleRow(DoseSchedule.IntervalDays(3, 1), {}, tablets)
                DoseScheduleRow(DoseSchedule.AsNeeded(10), {}, tablets)
                DoseScheduleRow(DoseSchedule.Unknown("framtida"), {}, tablets)
            }
        }
        captureLightAndDark("QuantityRuleRow_typer") {
            Sheet {
                QuantityRuleRow(QuantityRule.PerDays(1, 7), {}, { if (it == 1) "tub" else "tuber" })
                QuantityRuleRow(QuantityRule.PerTraveller(1), {}, { "st" })
                QuantityRuleRow(QuantityRule.Unknown(), {}, { "st" })
            }
        }
        captureLightAndDark("PackingPreview_standard") {
            Sheet {
                PackingPreview("För en 7-dagarsresa", "18 tabletter · 2 kartor", reason = "2 × 1 tablett/dag × (7 + 2) dagar")
                PackingPreview("För 4 personer i 10 dagar", "2 tuber")
            }
        }
    }

    @Test
    fun `progress, laddning och navigering`() {
        captureLightAndDark("SuitcaseProgress_varianter") {
            Sheet {
                ApotekCard { SuitcaseProgress(9, 14) }
                SuitcaseProgress(4, 10, compact = true)
                SuitcaseProgress(0, 0, compact = true)
            }
        }
        captureLightAndDark("AppLoading_standard") { Box(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.xxl)) { AppLoading() } }
        captureLightAndDark("AppTopBar_storlekar") {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                AppTopBar("Resor") { AppMenu(emptyList()) }
                AppTopBar("Solkräm", size = TopBarSize.Small, onBack = {}) { AppButton("Spara", {}, compact = true) }
            }
        }
        captureLightAndDark("AppFloatingToolbar_resor") {
            Box(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.l)) {
                AppFloatingToolbar(
                    listOf(
                        ToolbarItem("Resor", R.drawable.ic_luggage),
                        ToolbarItem("Personer", R.drawable.ic_person),
                        ToolbarItem("Familjeartiklar", R.drawable.ic_family_items),
                        ToolbarItem("Inställningar", R.drawable.ic_settings),
                    ),
                    selectedIndex = 0,
                    onSelect = {},
                )
            }
        }
    }
}
