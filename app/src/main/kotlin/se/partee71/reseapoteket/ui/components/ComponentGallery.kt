package se.partee71.reseapoteket.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.model.DoseSchedule
import se.partee71.reseapoteket.core.model.QuantityRule
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.ui.common.DetailUiState
import se.partee71.reseapoteket.ui.common.EditorUiState
import se.partee71.reseapoteket.ui.common.ArchiveEvent
import se.partee71.reseapoteket.ui.common.ListUiState
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.reseapoteket.ui.theme.AppTypography
import se.partee71.reseapoteket.ui.theme.Spacing
import se.partee71.reseapoteket.ui.theme.Tone

/**
 * Alla komponenter och ramar på en skärm, med riktiga svenska texter (skill
 * shared-ui-components). Nås från Inställningar → "Komponenter" i debug-bygget.
 * `UiConsistencyTest` kontrollerar att varje publik komponent finns här.
 */
@Composable
fun ComponentGallery(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        AppTopBar("Komponenter", size = TopBarSize.Small, onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            GallerySection("Knappar och meny") { Buttons() }
            GallerySection("Kort, rader och rubriker") { Rows() }
            GallerySection("Fält, val och mängder") { Fields() }
            GallerySection("Progress och navigering") { Progress() }
            GallerySection("Dialog, sheet, meddelanden") { Overlays() }
            GallerySection("Ramar") { Frames(onBack) }
        }
    }
}

@Composable
private fun GallerySection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(title, style = AppTypography.headline, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun Label(text: String) = GroupLabel(text)

@Composable
private fun Buttons() {
    Label("AppButton")
    AppButton("Ny resa", {}, Modifier.fillMaxWidth(), icon = R.drawable.ic_add)
    AppButton("Sparar …", {}, Modifier.fillMaxWidth(), loading = true)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        AppButton("Lägg till", {}, variant = ButtonVariant.Secondary)
        AppButton("Avbryt", {}, variant = ButtonVariant.Text)
        AppButton("Spara", {}, enabled = false)
    }
    AppButton("Radera", {}, variant = ButtonVariant.Destructive)
    Label("AppIconButton · AppMenu · AddSplitButton")
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        AppIconButton(R.drawable.ic_arrow_back, "Tillbaka", {}, variant = IconButtonVariant.Tonal)
        AppIconButton(R.drawable.ic_add, "Lägg till", {}, variant = IconButtonVariant.Tonal)
        AppMenu(sampleMenu())
    }
    AddSplitButton("Lägg till", {}, menuItems = listOf(AppMenuItem("Räkna om", {}), AppMenuItem("Dölj packade", {})))
    AddSplitButton("Lägg till person", {})
}

@Composable
private fun sampleMenu() = listOf(
    AppMenuItem("Arkivera", {}, R.drawable.ic_archive),
    AppMenuItem("Radera", {}, R.drawable.ic_delete, destructive = true),
)

@Composable
private fun Rows() {
    var packed by remember { mutableStateOf(false) }
    var sorted by remember { mutableStateOf(true) }
    ApotekCard {
        SectionHeader("Mediciner", icon = R.drawable.ic_medication, count = "4 / 5")
        ItemRow(
            "Anna",
            subtitle = "3 recept · 1 kosttillskott",
            leading = { PersonAvatar("🦊", AppColors.person(0)) },
            navigates = true,
            onClick = {},
        )
        AppDivider()
        ItemRow(
            "Gran Canaria",
            subtitle = "fre 2 okt 2026 · 7 dagar",
            trailing = { InfoPill("Planeras", tone = Tone.Blue) },
            onClick = {},
            navigates = true,
            below = {
                AvatarStack(listOf(StackedAvatar("🦊", AppColors.person(0), "Anna"), StackedAvatar("🐻", AppColors.person(1), "Erik")))
            },
        )
        AppDivider()
        PausableRow("Solkräm SPF 50", "1 tub per 7 dagar", "Per N dagar", active = true, onClick = {})
        AppDivider()
        PausableRow("Hydrokortison 1 %", "1 tub", "Fast antal", active = false, onClick = {})
        AppDivider()
        CheckRow("Enalapril 10 mg", packed, { packed = it }, subtitle = "18 tabletter · 2 kartor", trailing = { InfoPill("1/dag", tone = Tone.Blue) })
        CheckRow("Levaxin 50 µg", true, {}, subtitle = "9 tabletter · 1 karta")
        CheckRow("Ventoline", false, {}, subtitle = "1 st", onClick = {}, accent = AppColors.person(1), trailing = { InfoPill("vid behov", tone = Tone.Blue) })
        AppDivider()
        SwitchRow("Flytta avprickade sist", sorted, { sorted = it }, subtitle = "I packlistan")
        SwitchRow("Dags att packa", sorted, { sorted = it }, subtitle = "3 dagar före · 18:00", onClick = {})
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        InfoPill("Mediciner")
        InfoPill("Kosttillskott", tone = Tone.Green)
        InfoPill("Övrigt", tone = Tone.Blue)
    }
    InfoPill(stringResource(R.string.sync_pending), tone = Tone.Blue, icon = R.drawable.ic_cloud_upload, onClick = {})
    ChipRow {
        listOf("🦊", "🐻", "🐰", "🦉", "🐯", "🐢", "🦄", "🐧").forEachIndexed { i, emoji -> PersonAvatar(emoji, AppColors.person(i)) }
    }
    PersonAvatar("🦉", AppColors.person(3), size = AvatarSize.Large)
}

@Composable
private fun Fields() {
    var name by remember { mutableStateOf("Solkräm") }
    var date by remember { mutableStateOf<LocalDate?>(LocalDate(2026, 12, 19)) }
    var form by remember { mutableIntStateOf(0) }
    var theme by remember { mutableIntStateOf(2) }
    var count by remember { mutableIntStateOf(7) }
    var schedule by remember { mutableStateOf<DoseSchedule>(DoseSchedule.Daily(dosesPerDay = 2)) }
    var rule by remember { mutableStateOf<QuantityRule>(QuantityRule.PerDays(1, 7)) }
    val selected = remember { mutableStateOf(setOf("Anna", "Erik")) }
    AppTextField(name, { name = it }, "Namn", helper = "Visas i packlistan")
    AppTextField("", {}, "Namn", error = "Ange ett namn")
    DateField("Startdatum", date, { date = it })
    var time by remember { mutableStateOf(LocalTime(18, 0)) }
    TimeField("Tid", time, { time = it })
    var travelDay by remember { mutableStateOf(true) }
    var travelTime by remember { mutableStateOf<LocalTime?>(null) }
    TravelDayField(travelDay, travelTime, { travelDay = it.on; travelTime = it.ownTime })
    LabeledGroup("Form") {
        ChoiceChips(listOf(0, 1, 2, 3), form, { form = it }, { listOf("Tablett", "Kapsel", "Flytande", "Inhalator")[it] })
    }
    FieldError("Ingen anslutning just nu. Det du sparar skickas när nätet är tillbaka.")
    AppFilterChip("Flytande", selected = false, onClick = {}, enabled = false)
    ExampleChips(listOf("D-vitamin", "Omega-3", "Magnesium"), {}, chosen = "D-vitamin")
    ChipRow(wrap = false) {
        listOf("Anna" to "🦊", "Erik" to "🐻", "Lova" to "🐰").forEachIndexed { i, (person, emoji) ->
            PersonChip(person, emoji, AppColors.person(i), person in selected.value, {
                selected.value = if (person in selected.value) selected.value - person else selected.value + person
            })
        }
    }
    AppSegmentedChoice(listOf("Ljust", "Mörkt", "System"), theme, { theme = it })
    QuantityStepper(count, { count = it }, "dagar")
    DoseScheduleRow(
        schedule,
        { schedule = it },
        unitName = { if (it == 1) "tablett" else "tabletter" },
        error = "Välj minst en veckodag".takeIf { (schedule as? DoseSchedule.Weekdays)?.days?.isEmpty() == true },
    )
    PackingPreview("För en 7-dagarsresa", "18 tabletter · 2 kartor", reason = "2 × 1 tablett/dag × (7 + 2) dagar")
    QuantityRuleRow(rule, { rule = it }, unitName = { if (it == 1) "tub" else "tuber" })
    var emoji by remember { mutableStateOf("🦉") }
    var color by remember { mutableStateOf(AppColors.PERSON_HEX[3]) }
    Label("EmojiPicker")
    EmojiPicker(emoji, { emoji = it })
    Label("ColorSwatchPicker")
    ColorSwatchPicker(color, { color = it })
}

@Composable
private fun Progress() {
    ApotekCard { SuitcaseProgress(packed = 9, total = 14) }
    ApotekCard(tone = Tone.Green) { SuitcaseProgress(packed = 14, total = 14) }
    SuitcaseProgress(packed = 4, total = 10, compact = true)
    Box(Modifier.fillMaxWidth().height(96.dp)) { AppLoading() }
    var tab by remember { mutableIntStateOf(0) }
    AppFloatingToolbar(
        items = listOf(
            ToolbarItem("Resor", R.drawable.ic_luggage),
            ToolbarItem("Personer", R.drawable.ic_person),
            ToolbarItem("Familjeartiklar", R.drawable.ic_family_items),
            ToolbarItem("Inställningar", R.drawable.ic_settings),
        ),
        selectedIndex = tab,
        onSelect = { tab = it },
    )
    CompositionLocalProvider(LocalSyncIndicator provides SyncIndicator(pending = true)) {
        AppTopBar("Gran Canaria", size = TopBarSize.Small, onBack = {})
    }
}

@Composable
private fun Overlays() {
    var dialog by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var celebrate by remember { mutableStateOf(false) }
    var archived by remember { mutableStateOf<UndoRequest?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        AppButton("Dialog", { dialog = true }, variant = ButtonVariant.Secondary)
        AppButton("Sheet", { sheet = true }, variant = ButtonVariant.Secondary)
        AppButton("Snackbar", { scope.launch { snackbar.showSnackbar("Ingen anslutning just nu.") } }, variant = ButtonVariant.Secondary)
    }
    AppButton("Arkivera Solkräm", { archived = UndoRequest("solkram", "Solkräm") }, variant = ButtonVariant.Text)
    UndoSnackbar(archived, snackbar, onUndo = { archived = null }, onDismissed = { archived = null })
    if (dialog) {
        ConfirmDialog("Radera Alvedon?", "Receptet tas bort för gott. Det går inte att ångra.", "Radera", { dialog = false }, { dialog = false }, destructive = true)
    }
    if (sheet) {
        AppBottomSheet("Levaxin 50 µg", onDismiss = { sheet = false }) {
            QuantityStepper(12, {}, "tabletter")
            AppButton("Återställ till förslag (9)", {}, variant = ButtonVariant.Text)
        }
    }
    EmptyState(R.drawable.ic_family_items, "Inga familjeartiklar än", "Solkräm, plåster, febertermometer – saker hela familjen delar.") {
        AppButton("Lägg till", {}, Modifier.fillMaxWidth(), icon = R.drawable.ic_add)
    }
    Box(Modifier.fillMaxWidth().height(120.dp)) {
        AppButton("Allt packat!", { celebrate = !celebrate }, Modifier.align(Alignment.Center), variant = ButtonVariant.Text)
        Confetti(Modifier.fillMaxSize(), play = celebrate)
    }
    AppSnackbarHost(snackbar)
}

@Composable
private fun Frames(onBack: () -> Unit) {
    var undo by remember { mutableStateOf<UndoRequest?>(null) }
    var items by remember { mutableStateOf(listOf("Solkräm", "Plåster", "Febertermometer")) }
    Label("EntityListScreen · SwipeToHide · UndoSnackbar")
    Box(Modifier.fillMaxWidth().height(FRAME_HEIGHT)) {
        EntityListScreen(
            title = "Familjeartiklar",
            state = if (items.isEmpty()) ListUiState.Empty else ListUiState.Content(items),
            empty = EmptyContent(
                R.drawable.ic_family_items,
                "Inga familjeartiklar än",
                "Solkräm, plåster, febertermometer.",
                "Lägg till",
                examples = listOf(EmptyExample("Solkräm") { items = listOf("Solkräm") }),
            ),
            onAdd = {},
            key = { it },
            archive = ListArchive(undo = undo) { event ->
                when (event) {
                    ArchiveEvent.Undo -> {
                        undo?.let { items = items + it.name }
                        undo = null
                    }
                    ArchiveEvent.UndoDismissed -> undo = null
                    else -> Unit
                }
            },
        ) { item ->
            SwipeToHide(onHide = { items = items - item; undo = UndoRequest(item, item) }) { swipe ->
                ItemRow(item, swipe, subtitle = "1 per resenär", navigates = true, onClick = {})
            }
        }
    }
    Label("EntityListScreen – fel")
    Box(Modifier.fillMaxWidth().height(FRAME_HEIGHT)) {
        EntityListScreen<String>(
            title = "Personer",
            state = ListUiState.Error(DataError.Offline),
            empty = EmptyContent(R.drawable.ic_person, "Inga personer än", "", "Lägg till person"),
            onAdd = {},
            key = { it },
        ) {}
    }
    Label("EntityListScreen – rubrik, grupper, undergrupper, kolumner, hopfällbar")
    val grouped = listOf("Anna" to "Levaxin 50 µg", "Erik" to "Ventoline", "" to "Solkräm", "" to "Plåster", "" to "Myggmedel", "-" to "Halstabletter")
    Box(Modifier.fillMaxWidth().height(FRAME_HEIGHT)) {
        EntityListScreen(
            title = "Kreta",
            subtitle = "2 okt · 7 dagar",
            state = ListUiState.Content(grouped),
            empty = EmptyContent(R.drawable.ic_family_items, "", "", ""),
            onAdd = {},
            key = { it.second },
            onBack = {},
            header = { ApotekCard { SuitcaseProgress(packed = 2, total = 5) } },
            group = { (who, _) ->
                when (who) {
                    "" -> ListGroup("Övrigt", count = "1 / 3", columns = 2)
                    "-" -> ListGroup("Dolda", count = "1", collapsible = true)
                    else -> ListGroup("Mediciner", tone = Tone.Blue, count = "1 / 2")
                }
            },
            subgroup = { (who, _) -> if (who.length > 1) ListSubgroup(who, AppColors.person(if (who == "Anna") 0 else 1)) else null },
        ) { (_, name) -> CheckRow(name, name.startsWith("S") || name.startsWith("L"), {}) }
    }
    Label("EntityDetailScreen – finns inte längre")
    Box(Modifier.fillMaxWidth().height(FRAME_HEIGHT)) {
        EntityDetailScreen<String>(DetailUiState.Error(DataError.NotFound), header = { DetailHeader(it) }, onBack = {}) {}
    }
    Label("EntityEditScreen")
    var name by remember { mutableStateOf("") }
    Box(Modifier.fillMaxWidth().height(FRAME_HEIGHT)) {
        EntityEditScreen(
            title = "Ny familjeartikel",
            state = EditorUiState(value = name, errors = if (name.isBlank()) mapOf("name" to R.string.error_unknown) else emptyMap(), isValid = name.isNotBlank(), isDirty = name.isNotEmpty()),
            effects = emptyFlow(),
            onSave = {},
            onClose = onBack,
            onArchive = {},
            delete = DeleteAction("Radera Solkräm?", "Artikeln tas bort för gott. Det går inte att ångra.") {},
        ) {
            AppTextField(name, { name = it }, "Namn")
        }
    }
    Label("EntityDetailScreen")
    Box(Modifier.fillMaxWidth().height(FRAME_HEIGHT)) {
        EntityDetailScreen(
            state = DetailUiState.Content("Anna"),
            header = { DetailHeader(it, "3 recept · 1 kosttillskott") },
            onBack = onBack,
            onEdit = {},
            menu = { listOf(AppMenuItem("Arkivera", {}, R.drawable.ic_archive)) },
            leading = { PersonAvatar("🦊", AppColors.person(0), size = AvatarSize.Large) },
        ) {
            ApotekCard {
                SectionHeader("Recept", icon = R.drawable.ic_medication, count = "1")
                ItemRow("Levaxin 50 µg", subtitle = "1 tablett dagligen", navigates = true, onClick = {})
            }
        }
    }
}

private val FRAME_HEIGHT = 480.dp
