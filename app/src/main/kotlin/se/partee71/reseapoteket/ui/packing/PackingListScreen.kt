package se.partee71.reseapoteket.ui.packing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.engine.PackingGroup
import se.partee71.reseapoteket.core.engine.PackingList
import se.partee71.reseapoteket.core.engine.PackingProgress
import se.partee71.reseapoteket.core.model.PackingItem
import se.partee71.reseapoteket.core.model.PackingSection
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.ui.common.DateFormat
import se.partee71.reseapoteket.ui.common.EditorUiState
import se.partee71.reseapoteket.ui.common.ListEvent
import se.partee71.reseapoteket.ui.common.ListUiState
import se.partee71.reseapoteket.ui.common.collectAsListArchive
import se.partee71.reseapoteket.ui.components.ApotekCard
import se.partee71.reseapoteket.ui.components.AppButton
import se.partee71.reseapoteket.ui.components.AppIconButton
import se.partee71.reseapoteket.ui.components.AppMenuItem
import se.partee71.reseapoteket.ui.components.AvatarStack
import se.partee71.reseapoteket.ui.components.ButtonVariant
import se.partee71.reseapoteket.ui.components.CheckRow
import se.partee71.reseapoteket.ui.components.Confetti
import se.partee71.reseapoteket.ui.components.ConfirmDialog
import se.partee71.reseapoteket.ui.components.EmptyContent
import se.partee71.reseapoteket.ui.components.EntityListScreen
import se.partee71.reseapoteket.ui.components.InfoPill
import se.partee71.reseapoteket.ui.components.ItemRow
import se.partee71.reseapoteket.ui.components.ListArchive
import se.partee71.reseapoteket.ui.components.ListGroup
import se.partee71.reseapoteket.ui.components.ListSubgroup
import se.partee71.reseapoteket.ui.components.LocalTravelDayMorning
import se.partee71.reseapoteket.ui.components.StackedAvatar
import se.partee71.reseapoteket.ui.components.SuitcaseProgress
import se.partee71.reseapoteket.ui.components.SwipeToHide
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.reseapoteket.ui.theme.Spacing
import se.partee71.reseapoteket.ui.theme.Tone

/** Packlistan för en resa (PAK-1–7); pennan öppnar resans formulär (RES-1). */
@Composable
fun PackingListScreen(onBack: () -> Unit, onEditTrip: () -> Unit, viewModel: PackingListViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val header by viewModel.header.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val adding by viewModel.adding.collectAsStateWithLifecycle()
    val addState by viewModel.addEditor.state.collectAsStateWithLifecycle()
    val addList by viewModel.addList.collectAsStateWithLifecycle()
    val askRecount by viewModel.askRecount.collectAsStateWithLifecycle()
    val sheetError by viewModel.sheetError.collectAsStateWithLifecycle()
    PackingListContent(
        state = state,
        header = header,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onEditTrip = onEditTrip,
        archive = viewModel.hiding.collectAsListArchive(showToggle = false),
        editing = editing,
        adding = addState.takeIf { adding },
        addList = addList,
        askRecount = askRecount,
        celebrations = viewModel.celebrations,
        sheetError = sheetError,
    )
}

@Composable
fun PackingListContent(
    state: ListUiState<PackingRow>,
    header: PackingHeader?,
    onEvent: (PackingListEvent) -> Unit,
    onBack: () -> Unit,
    onEditTrip: () -> Unit,
    modifier: Modifier = Modifier,
    archive: ListArchive = ListArchive(showToggle = false),
    editing: PackingRow? = null,
    adding: EditorUiState<PackingItem>? = null,
    addList: AddListState = AddListState(),
    askRecount: Boolean = false,
    celebrations: Flow<Unit> = emptyFlow(),
    sheetError: DataError? = null,
) {
    val groups = packingGroups(header?.groups.orEmpty())
    val progress = header?.progress ?: PackingProgress.NONE
    var confetti by remember { mutableStateOf(false) }
    LaunchedEffect(celebrations) { celebrations.collect { confetti = true } }
    Box(modifier.fillMaxSize()) {
        EntityListScreen(
            title = header?.trip?.name.orEmpty(),
            state = state,
            empty = EmptyContent(
                icon = R.drawable.ic_luggage,
                title = stringResource(R.string.packing_empty_title),
                message = stringResource(R.string.packing_empty_message),
                actionLabel = stringResource(R.string.add),
            ),
            onAdd = { onEvent(PackingListEvent.Add) },
            key = { it.item.id },
            onRetry = { onEvent(PackingListEvent.List(ListEvent.Retry)) },
            addMenu = listOf(
                AppMenuItem(stringResource(R.string.packing_recount), { onEvent(PackingListEvent.AskRecount) }, R.drawable.ic_refresh),
                AppMenuItem(stringResource(R.string.packing_hide_checked), { onEvent(PackingListEvent.HideChecked) }, R.drawable.ic_visibility_off),
            ),
            group = { row -> groups.getValue(if (row.item.hidden) null else PackingList.group(row.item)) },
            archive = archive,
            subtitle = header?.let { subtitle(it) },
            onBack = onBack,
            actions = {
                // Utan resa (inte läst än, eller finns inte) finns inget att ändra.
                header?.let { h ->
                    AvatarStack(h.travellers.map { StackedAvatar(it.emoji, AppColors.person(it.colorHex), it.name) })
                    AppIconButton(R.drawable.ic_edit, stringResource(R.string.trip_edit), onEditTrip)
                }
            },
            header = header?.let {
                {
                    ApotekCard(tone = if (progress.isComplete) Tone.Green else null) { SuitcaseProgress(progress.packed, progress.total) }
                }
            },
            subgroup = { row ->
                row.person?.takeIf { !row.item.hidden && row.item.section != PackingSection.FAMILY }?.let { ListSubgroup(it.name, AppColors.person(it.colorHex)) }
            },
        ) { row -> PackingRowView(row, onEvent, archive) }
        if (confetti) Confetti(Modifier.fillMaxSize(), onFinished = { confetti = false })
    }
    editing?.let { QuantitySheet(it, onEvent, sheetError) }
    adding?.let { AddItemSheet(it, header?.travellers.orEmpty(), onEvent, sheetError, addList) }
    if (askRecount) {
        ConfirmDialog(
            title = stringResource(R.string.trip_recount_title),
            message = stringResource(R.string.packing_recount_message),
            confirmLabel = stringResource(R.string.trip_recount_confirm),
            onConfirm = { onEvent(PackingListEvent.Recount) },
            onDismiss = { onEvent(PackingListEvent.CancelRecount) },
        )
    }
}

/** "fre 2 okt 2026 · 7 dagar · 4 resenärer" (PAK-1). */
@Composable
private fun subtitle(header: PackingHeader): String {
    val trip = header.trip
    val days = pluralStringResource(R.plurals.trip_days, trip.days, trip.days)
    val travellers = pluralStringResource(R.plurals.packing_travellers, header.travellers.size, header.travellers.size)
    return listOfNotNull(trip.startDate?.let(DateFormat::display), days, travellers).joinToString(" · ")
}

/**
 * Rubrik per grupp med packat/totalt; "På resedagen" efter Övrigt och "Dolda" (`null`) sist och
 * hopfälld (PAK-1, PAK-4, PAK-6).
 */
@Composable
private fun packingGroups(progress: Map<PackingGroup, PackingProgress>): Map<PackingGroup?, ListGroup> {
    fun count(group: PackingGroup) = progress[group]?.let { "${it.packed} / ${it.total}" }
    val medicines = ListGroup(stringResource(R.string.packing_medicines), R.drawable.ic_medication, Tone.Primary, count(PackingGroup.MEDICINE))
    val supplements = ListGroup(stringResource(R.string.packing_supplements), R.drawable.ic_eco, Tone.Green, count(PackingGroup.SUPPLEMENT))
    val other = ListGroup(stringResource(R.string.packing_other), R.drawable.ic_box, Tone.Blue, count(PackingGroup.FAMILY), columns = 2)
    val travelDay = ListGroup(stringResource(R.string.packing_travel_day), R.drawable.ic_sun, Tone.Primary, count(PackingGroup.TRAVEL_DAY))
    val hidden = ListGroup(stringResource(R.string.packing_hidden), R.drawable.ic_visibility_off, Tone.Primary, collapsible = true)
    return remember(medicines, supplements, other, travelDay, hidden) {
        mapOf(
            PackingGroup.MEDICINE to medicines,
            PackingGroup.SUPPLEMENT to supplements,
            PackingGroup.FAMILY to other,
            PackingGroup.TRAVEL_DAY to travelDay,
            null to hidden,
        )
    }
}

/** En rad: kryss och mängd; tryck öppnar mängden, svep döljer. En dold rad har "Visa igen". */
@Composable
private fun PackingRowView(row: PackingRow, onEvent: (PackingListEvent) -> Unit, archive: ListArchive) {
    val item = row.item
    val amount = PackingList.amount(item)
    if (item.hidden) {
        ItemRow(
            item.name,
            subtitle = amount,
            trailing = { AppButton(stringResource(R.string.packing_show_again), { onEvent(PackingListEvent.Show(item.id)) }, variant = ButtonVariant.Text, compact = true) },
        )
        return
    }
    SwipeToHide(onHide = { archive.archive(item.id, item.name) }, label = stringResource(R.string.packing_hide)) { swipe ->
        CheckRow(
            item.name,
            item.checked,
            { onEvent(PackingListEvent.Check(item.id, it)) },
            swipe,
            subtitle = amount,
            trailing = labels(row),
            onClick = { onEvent(PackingListEvent.Open(item.id)) },
            onClickLabel = stringResource(R.string.packing_edit_quantity),
            accent = row.person?.takeIf { item.section != PackingSection.FAMILY }?.let { AppColors.person(it.colorHex) },
        )
    }
}

/**
 * Etiketterna på en rad: ändrad mängd eller dosering (PAK-2) och, för det som packas på
 * resedagen, när (egen tid eller resedagens morgon, PAK-6). Utan etikett ingen yta alls.
 */
private fun labels(row: PackingRow): (@Composable () -> Unit)? {
    val item = row.item
    val changed = PackingList.isChanged(item)
    if (!changed && row.schedule == null && !item.travelDay) return null
    return {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            when {
                changed -> InfoPill(stringResource(R.string.packing_changed))
                row.schedule != null -> InfoPill(row.schedule, tone = Tone.Blue)
            }
            if (item.travelDay) InfoPill(DateFormat.time(item.travelDayTime ?: LocalTravelDayMorning.current))
        }
    }
}
