package app.aaps.ui.compose.alertKinds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.alerts.AlertChoice
import app.aaps.core.interfaces.alerts.AlertGroup
import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertSection
import app.aaps.core.interfaces.alerts.AlertSetting
import app.aaps.core.interfaces.alerts.AlertSound
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/**
 * Ring, Vibrate (where the phone can), Silent or Off on this phone (`_docs/ALARMS.md`, section 6), in four sections: the pump and the CGM by level
 * (critical, needs action, info), the BG alarms (lows, highs) and the app's own alerts one by one (decided 2026-11-26).
 * A change applies at once, so there is no Save: the back arrow only goes back. A protected row shows that it always
 * rings, with no choice.
 *
 * A kind of a group can be set apart from it as an exception: the alarm screen's "Silence this kind" makes one, and
 * "Add exception" here. Each row that makes a sound has its sound on this phone ([sounds], by group or kind id; not in
 * it: the alert's own) - one AAPS ships, or on Android any sound of the phone - and a Test button, which plays it as an
 * alarm for a few seconds, also in Do Not Disturb, or vibrates on a row set to Vibrate. [onTest] returns false when
 * nothing was played: an alarm is ringing.
 */
@Composable
fun AlertKindsScreen(
    sections: List<AlertSettingsSection>,
    /** The phone can vibrate, so Vibrate is offered and explained. */
    vibrates: Boolean,
    silentOrOff: Int,
    onChoice: (AlertSetting, AlertChoice) -> Unit,
    onClear: (AlertKind) -> Unit,
    sounds: Map<String, AlertSound>,
    onSound: (AlertSetting, AlertSound?) -> Unit,
    onTest: (kind: AlertKind, vibrate: Boolean) -> Boolean,
    onBgRules: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Held by the caller, so the list is where it was when the user comes back from the BG rules. */
    listState: LazyListState = rememberLazyListState()
) {
    var addingTo by remember { mutableStateOf<AlertSettingsSection?>(null) }
    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(UiStrings.alert_kinds_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreUiStrings.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
                    Text(stringResource(UiStrings.alert_kinds_intro), style = MaterialTheme.typography.bodyMedium)
                    if (vibrates) Text(stringResource(UiStrings.alert_kinds_vibrate_intro), style = MaterialTheme.typography.bodyMedium)
                    if (silentOrOff > 0)
                        Text(stringResource(UiStrings.alert_kinds_silent_or_off, silentOrOff), style = MaterialTheme.typography.bodyMedium)
                }
            }
            sections.forEach { section ->
                item(key = "section:" + section.section.name) { SectionHeading(section, onBgRules) }
                items(section.rows, key = { it.setting.id }) { row -> SettingRowView(row, sounds[row.setting.id], onChoice, onSound, onTest) }
                if (section.exceptions.isNotEmpty())
                    item(key = "exceptions:" + section.section.name) {
                        Text(stringResource(UiStrings.alert_kind_exceptions), style = MaterialTheme.typography.titleSmall)
                    }
                items(section.exceptions, key = { "exception:" + it.kind.id }) { row -> ExceptionRowView(row, onChoice, onClear) }
                if (section.candidates.isNotEmpty())
                    item(key = "add:" + section.section.name) {
                        TextButton(onClick = { addingTo = section }) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text(stringResource(UiStrings.alert_kind_add_exception))
                        }
                    }
            }
        }
    }
    addingTo?.let { section ->
        AddExceptionDialog(
            candidates = section.candidates,
            onPick = { kind ->
                addingTo = null
                kind.group?.let { group ->
                    val groupChoice = section.rows.firstOrNull { it.setting == group }?.choice ?: AlertChoice.RING
                    onChoice(kind, firstExceptionChoice(group, groupChoice))
                }
            },
            onDismiss = { addingTo = null }
        )
    }
}

@Composable
private fun SectionHeading(section: AlertSettingsSection, onBgRules: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
        HorizontalDivider()
        Text(stringResource(section.section.title()), style = MaterialTheme.typography.titleMedium)
        section.devices.forEach { device ->
            Text(stringResource(device), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The levels themselves are the rules, the same on every phone; here only how this phone rings
        if (section.section == AlertSection.BG)
            TextButton(onClick = onBgRules) {
                Text(stringResource(UiStrings.bg_alarm_rules_title))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
    }
}

@Composable
private fun SettingRowView(
    row: AlertSettingRow,
    sound: AlertSound?,
    onChoice: (AlertSetting, AlertChoice) -> Unit,
    onSound: (AlertSetting, AlertSound?) -> Unit,
    onTest: (kind: AlertKind, vibrate: Boolean) -> Boolean
) {
    val setting = row.setting
    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
        Column {
            Text(stringResource(setting.title), style = MaterialTheme.typography.bodyLarge)
            if (setting is AlertGroup)
                Text(stringResource(setting.summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (setting.isProtected) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(UiStrings.alert_kind_protected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else ChoiceButtons(row.choices, row.choice) { onChoice(setting, it) }
        // The Test does what the row's choice does: on Vibrate it vibrates
        row.testKind?.let { kind -> SoundRow(setting, sound, onSound) { onTest(kind, row.choice == AlertChoice.VIBRATE) } }
    }
}

/** A kind set apart from its group: its name, the button that removes the exception, and its own choice. */
@Composable
private fun ExceptionRowView(row: AlertExceptionRow, onChoice: (AlertSetting, AlertChoice) -> Unit, onClear: (AlertKind) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(row.kind.title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { onClear(row.kind) }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(UiStrings.alert_kind_remove_exception))
            }
        }
        ChoiceButtons(row.choices, row.choice) { onChoice(row.kind, it) }
    }
}

/** Ring, Vibrate (where the phone can), Silent and Off; for a row that makes no sound, Show and Off. */
@Composable
private fun ChoiceButtons(choices: List<AlertChoice>, selected: AlertChoice, onPick: (AlertChoice) -> Unit) {
    val quiet = AlertChoice.SILENT !in choices
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        choices.forEachIndexed { index, choice ->
            SegmentedButton(
                selected = selected == choice,
                onClick = { onPick(choice) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = choices.size),
                icon = {}
            ) {
                Text(stringResource(choice.label(quiet)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The kinds that can be set apart from their group; picking one adds its exception. */
@Composable
private fun AddExceptionDialog(candidates: List<AlertKind>, onPick: (AlertKind) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(UiStrings.alert_kind_add_exception)) },
        text = {
            LazyColumn {
                items(candidates, key = { it.id }) { kind ->
                    ListItem(
                        headlineContent = { Text(stringResource(kind.title)) },
                        modifier = Modifier.clickable { onPick(kind) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiStrings.cancel)) } }
    )
}

/** The sound of a row on this phone, to pick from the sounds AAPS ships or, on Android, the phone's; and its Test. */
@Composable
private fun SoundRow(setting: AlertSetting, sound: AlertSound?, onSound: (AlertSetting, AlertSound?) -> Unit, onTest: () -> Boolean) {
    var picking by remember { mutableStateOf(false) }
    val ringtonePicker = rememberRingtonePicker { uri, title -> onSound(setting, AlertSound.Ringtone(uri, title)) }
    var refused by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
        Text(stringResource(UiStrings.alert_kind_sound), style = MaterialTheme.typography.bodyMedium)
        Box {
            TextButton(onClick = { picking = true }) {
                Text(
                    when (sound) {
                        null                   -> stringResource(UiStrings.alert_kind_sound_own)
                        is AlertSound.Bundled  -> stringResource(sound.sound.title)
                        is AlertSound.Ringtone -> sound.title
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(UiStrings.alert_kind_sound_own)) },
                    onClick = {
                        picking = false
                        onSound(setting, null)
                    }
                )
                AlarmSound.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(stringResource(s.title)) },
                        onClick = {
                            picking = false
                            onSound(setting, AlertSound.Bundled(s))
                        }
                    )
                }
                ringtonePicker?.let { pick ->
                    DropdownMenuItem(
                        text = { Text(stringResource(UiStrings.alert_kind_sound_phone)) },
                        onClick = {
                            picking = false
                            pick((sound as? AlertSound.Ringtone)?.uri)
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = { refused = !onTest() }) { Text(stringResource(UiStrings.alert_kind_sound_test)) }
    }
    if (refused) Text(stringResource(UiStrings.alert_kind_sound_test_refused), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

private fun AlertChoice.label(quiet: Boolean): TextRef = when (this) {
    AlertChoice.RING    -> if (quiet) UiStrings.alert_choice_show else UiStrings.alert_choice_ring
    AlertChoice.VIBRATE -> UiStrings.alert_choice_vibrate
    AlertChoice.SILENT  -> UiStrings.alert_choice_silent
    AlertChoice.OFF     -> UiStrings.alert_choice_off
}

private fun AlertSection.title(): TextRef = when (this) {
    AlertSection.PUMP -> CoreUiStrings.pump
    AlertSection.CGM  -> CoreUiStrings.cgm
    AlertSection.BG   -> InterfacesStrings.alert_kinds_group_bg
    AlertSection.APP  -> InterfacesStrings.alert_kinds_group_app
}
