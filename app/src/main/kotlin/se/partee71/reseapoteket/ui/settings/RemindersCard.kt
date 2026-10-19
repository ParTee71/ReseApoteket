package se.partee71.reseapoteket.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.datetime.LocalDateTime
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.engine.ReminderKind
import se.partee71.reseapoteket.core.engine.ReminderSetting
import se.partee71.reseapoteket.core.engine.ReminderSettings
import se.partee71.reseapoteket.reminders.canPostNotifications
import se.partee71.reseapoteket.ui.common.DateFormat
import se.partee71.reseapoteket.ui.components.ApotekCard
import se.partee71.reseapoteket.ui.components.AppBottomSheet
import se.partee71.reseapoteket.ui.components.ItemRow
import se.partee71.reseapoteket.ui.components.LabeledGroup
import se.partee71.reseapoteket.ui.components.PackingPreview
import se.partee71.reseapoteket.ui.components.PersonChip
import se.partee71.reseapoteket.ui.components.QuantityStepper
import se.partee71.reseapoteket.ui.components.SwitchRow
import se.partee71.reseapoteket.ui.components.TimeField
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.ui.components.AppButton
import se.partee71.ui.components.AppDivider
import se.partee71.ui.components.ChipRow
import se.partee71.ui.theme.AppTheme

@StringRes
private fun ReminderKind.title(): Int = when (this) {
    ReminderKind.NO_LIST -> R.string.reminder_no_list
    ReminderKind.TIME_TO_PACK -> R.string.reminder_time_to_pack
    ReminderKind.DAY_BEFORE -> R.string.reminder_day_before
    ReminderKind.TRAVEL_DAY -> R.string.reminder_travel_day
}

@StringRes
private fun ReminderKind.note(): Int = when (this) {
    ReminderKind.NO_LIST -> R.string.reminder_no_list_note
    ReminderKind.TIME_TO_PACK -> R.string.reminder_time_to_pack_note
    ReminderKind.DAY_BEFORE -> R.string.reminder_day_before_note
    ReminderKind.TRAVEL_DAY -> R.string.reminder_travel_day_note
}

/** "3 dagar före · 18:00", "18:00" eller "Av". */
@Composable
private fun summary(kind: ReminderKind, setting: ReminderSetting): String {
    if (!setting.enabled) return stringResource(R.string.reminder_off)
    val time = DateFormat.time(setting.time)
    if (!ReminderSettings.hasDaysBefore(kind)) return time
    return stringResource(R.string.reminder_at, pluralStringResource(R.plurals.reminder_days_before, setting.daysBefore, setting.daysBefore), time)
}

/**
 * Kortet Påminnelser i Inställningar (PÅM-1, PÅM-7): vilka personer telefonen påminner för och de
 * fyra påminnelserna. Tryck på en rad öppnar dess inställningar; behörigheten att visa notiser
 * frågas först när användaren slår på något. [notificationsAllowed] styr rutan "Notiser är avstängda".
 */
@Composable
fun RemindersCard(
    state: RemindersUiState,
    onEvent: (RemindersEvent) -> Unit,
    notificationsAllowed: Boolean,
    onAskPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit
) {
    var editing by rememberSaveable { mutableStateOf<ReminderKind?>(null) }
    val change = { kind: ReminderKind, setting: ReminderSetting ->
        if (setting.enabled) onAskPermission()
        onEvent(RemindersEvent.Change(kind, setting))
    }
    ApotekCard {
        if (!notificationsAllowed) {
            ItemRow(
                title = stringResource(R.string.reminders_blocked),
                subtitle = stringResource(R.string.reminders_blocked_note),
                trailing = { AppButton(stringResource(R.string.reminders_enable), onOpenNotificationSettings, compact = true) }
            )
            AppDivider()
        }
        ItemRow(
            title = stringResource(R.string.reminders_persons),
            subtitle = stringResource(R.string.reminders_persons_note),
            below = {
                ChipRow {
                    state.persons.forEach { person ->
                        val chosen = person.id in state.settings.personIds
                        PersonChip(person.name, person.emoji, AppColors.person(person.colorHex), chosen, {
                            if (!chosen) onAskPermission()
                            onEvent(RemindersEvent.TogglePerson(person.id))
                        })
                    }
                }
            }
        )
        AppDivider()
        ReminderKind.entries.forEach { kind ->
            val setting = state.settings[kind]
            SwitchRow(
                stringResource(kind.title()),
                setting.enabled,
                { change(kind, setting.copy(enabled = it)) },
                subtitle = summary(kind, setting),
                onClick = { editing = kind },
                onClickLabel = stringResource(R.string.reminder_open_settings)
            )
        }
    }
    editing?.let { kind -> ReminderSheet(kind, state, { change(kind, it) }, onClose = { editing = null }) }
}

/** En påminnelses inställningar: på/av, dagar före (där det går), tid och när nästa kommer. */
@Composable
private fun ReminderSheet(kind: ReminderKind, state: RemindersUiState, onChange: (ReminderSetting) -> Unit, onClose: () -> Unit) {
    val setting = state.settings[kind]
    AppBottomSheet(stringResource(kind.title()), onDismiss = onClose) {
        Text(stringResource(kind.note()), style = AppTheme.typography.itemSubtitle)
        SwitchRow(stringResource(kind.title()), setting.enabled, { onChange(setting.copy(enabled = it)) })
        if (ReminderSettings.hasDaysBefore(kind)) {
            LabeledGroup(stringResource(R.string.reminder_days_before_label)) {
                QuantityStepper(
                    setting.daysBefore,
                    { onChange(setting.copy(daysBefore = it)) },
                    pluralStringResource(R.plurals.reminder_days_before, setting.daysBefore, setting.daysBefore),
                    range = ReminderSettings.DAYS_BEFORE,
                    enabled = setting.enabled
                )
            }
        }
        TimeField(stringResource(R.string.reminder_time_label), setting.time, { onChange(setting.copy(time = it)) })
        val next = state.next[kind]
        PackingPreview(
            context = stringResource(R.string.reminder_next),
            quantity = next?.let { nextAt(it.at) } ?: stringResource(R.string.reminder_next_none),
            reason = next?.tripName
        )
        AppButton(stringResource(R.string.done), onClose, Modifier.fillMaxWidth())
    }
}

@Composable
private fun nextAt(at: LocalDateTime): String = stringResource(R.string.reminder_next_at, DateFormat.display(at.date), DateFormat.time(at.time))

/**
 * Om appen får visa notiser, kontrollerat varje gång skärmen visas, och hur man ber om det:
 * [NotificationAccess.ask] frågar (Android 13+) om det inte redan är tillåtet, [NotificationAccess.openSettings]
 * öppnar appens notisinställningar.
 */
class NotificationAccess(val allowed: Boolean, val ask: () -> Unit, val openSettings: () -> Unit)

@Composable
fun rememberNotificationAccess(): NotificationAccess {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(context.canPostNotifications()) }
    LifecycleResumeEffect(context) {
        allowed = context.canPostNotifications()
        onPauseOrDispose {}
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = context.canPostNotifications() }
    return NotificationAccess(
        allowed = allowed,
        ask = { if (!allowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        openSettings = { context.openNotificationSettings() }
    )
}

private fun Context.openNotificationSettings() {
    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
