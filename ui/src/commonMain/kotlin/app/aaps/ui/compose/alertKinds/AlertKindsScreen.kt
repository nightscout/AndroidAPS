package app.aaps.ui.compose.alertKinds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.AlertSound
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.ActionButtons
import app.aaps.core.ui.compose.ScreenAction
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/**
 * How each alert rings on this phone (`_docs/ALARMS.md`, section 6): a short list of the four parts - the pump, the CGM,
 * the BG alarms and the app's own alerts - each opening its own screen, as the settings of AAPS do (decided
 * 2026-11-26). Each part says the devices it is for and how many of its settings are silent or off here
 * ([sectionSilentOrOff]), so nothing is hidden by the extra step. On top a table says what each level (alarm, warning,
 * info) does with each choice ([LevelTable]), and the count of the whole phone ([silentOrOff]) and a pause of the BG
 * alarms ([pausedUntil]) are the same as the lines in the notification sheet.
 */
@Composable
fun AlertKindsScreen(
    sections: List<AlertSettingsSection>,
    /** The phone can vibrate, so Vibrate is offered and explained. */
    vibrates: Boolean,
    silentOrOff: Int,
    sectionSilentOrOff: (AlertSettingsSection) -> Int,
    onOpen: (AlertSection) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** The time the pause of the BG alarms on this phone ends; null: not paused. Its own line, not in the count. */
    pausedUntil: String? = null,
    /** Held by the caller, so the list is where it was when the user comes back from a part. */
    listState: LazyListState = rememberLazyListState()
) {
    val pauseText = pausedUntil?.let { stringResource(UiStrings.bg_alarms_paused_until, it) }
    Scaffold(topBar = { BackTopBar(stringResource(UiStrings.alert_kinds_title), onBack) }) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                    LevelTable(vibrates)
                    Text(stringResource(UiStrings.alert_kinds_table_note), style = MaterialTheme.typography.bodyMedium)
                    if (silentOrOff > 0)
                        Text(stringResource(UiStrings.alert_kinds_silent_or_off, silentOrOff), style = MaterialTheme.typography.bodyMedium)
                    pauseText?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            items(sections, key = { it.section.name }) { section ->
                // A pause is of the BG alarms only
                SectionCard(section, sectionSilentOrOff(section), pauseText.takeIf { section.section == AlertSection.BG }) { onOpen(section.section) }
            }
        }
    }
}

/**
 * What an alert does on this phone: its level in the columns, this phone's choice in the rows (decided 2026-11-27, the
 * user: "not clear how Alarm and Warning map to Ring, Vibrate, Silent, Off"). It replaced three paragraphs that said
 * the same in words. The Vibrate row only where the phone can vibrate.
 */
@Composable
private fun LevelTable(vibrates: Boolean) {
    val card = UiStrings.alert_cell_card
    val notShown = UiStrings.alert_cell_not_shown
    val rows = buildList {
        add(UiStrings.alert_choice_ring to listOf(UiStrings.alert_cell_rings_until_answered, UiStrings.alert_cell_one_sound, card))
        if (vibrates) add(UiStrings.alert_choice_vibrate to listOf(UiStrings.alert_cell_vibrates_until_answered, UiStrings.alert_cell_vibrates_once, card))
        add(UiStrings.alert_choice_silent to listOf(card, card, card))
        add(UiStrings.alert_choice_off to listOf(notShown, notShown, notShown))
    }
    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
        LevelTableRow(label = null, cells = listOf(CoreUiStrings.alarm, CoreUiStrings.warning, InterfacesStrings.alert_group_info), header = true)
        rows.forEach { (choice, cells) ->
            HorizontalDivider()
            LevelTableRow(label = choice, cells = cells, header = false)
        }
    }
}

@Composable
private fun LevelTableRow(label: TextRef?, cells: List<TextRef>, header: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
        Text(label?.let { stringResource(it) } ?: "", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(0.8f))
        cells.forEach { cell ->
            Text(
                stringResource(cell),
                style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** One part in the list: its name, the devices it is for or what it holds, what is silent or off in it, and a pause. */
@Composable
private fun SectionCard(section: AlertSettingsSection, silentOrOff: Int, pauseText: String?, onClick: () -> Unit) {
    AapsCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth().padding(AapsSpacing.extraLarge), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
                Text(stringResource(section.section.title()), style = MaterialTheme.typography.titleMedium)
                val detail = MaterialTheme.typography.bodyMedium
                val detailColor = MaterialTheme.colorScheme.onSurfaceVariant
                section.devices.forEach { device -> Text(stringResource(device), style = detail, color = detailColor) }
                section.section.summary()?.let { Text(stringResource(it), style = detail, color = detailColor) }
                if (silentOrOff > 0) Text(stringResource(UiStrings.alert_kinds_section_silent_or_off, silentOrOff), style = detail)
                pauseText?.let { Text(it, style = detail) }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/**
 * One part of the alert settings: a card for each of its rows ([AlertSettingsSection.rows]). A change applies at once,
 * so there is no Save: the back arrow only goes back. A protected row shows that it always rings, with no choice. Each
 * row is a card, so what belongs to it stays together, as in the other lists of AAPS (decided 2026-11-26).
 *
 * Each row says what Ring means for it: an alarm that rings until answered, or one sound ([AlertSettingRow.levels]); a
 * group whose alerts differ lists them with their level ("Its alerts", decided 2026-11-27).
 *
 * A kind of a group can be set apart from it as an exception: "Add exception" in the group's card. Each row that makes
 * a sound has its sound on this phone ([sounds], by group or
 * kind id; not in it: the alert's own) - one AAPS ships, or on Android any sound of the phone - and a Test button, which
 * plays it as an alarm for a few seconds, also in Do Not Disturb, or vibrates on a row set to Vibrate. [onTest] returns
 * false when nothing was played: an alarm is ringing.
 *
 * The BG alarms add what is set for every phone: [header] above the cards and [extra] in a row's card under its title;
 * and [actions], the screen's buttons, pinned at the bottom as on the pump and sync screens, with [actionsStatus] above
 * them, pinned too: the state they change, as "Paused until 21:30" over Resume.
 */
@Composable
fun AlertSectionScreen(
    section: AlertSettingsSection,
    onChoice: (AlertSetting, AlertChoice) -> Unit,
    onClear: (AlertKind) -> Unit,
    sounds: Map<String, AlertSound>,
    onSound: (AlertSetting, AlertSound?) -> Unit,
    onTest: (kind: AlertKind, vibrate: Boolean) -> Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    header: LazyListScope.() -> Unit = {},
    extra: @Composable ColumnScope.(AlertSettingRow) -> Unit = {},
    actions: List<ScreenAction> = emptyList(),
    actionsStatus: (@Composable () -> Unit)? = null
) {
    var addingTo by remember { mutableStateOf<AlertSettingRow?>(null) }
    Scaffold(topBar = { BackTopBar(stringResource(section.section.title()), onBack) }) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
            ) {
                if (section.devices.isNotEmpty()) item(key = "devices") {
                    Column {
                        section.devices.forEach { device ->
                            Text(stringResource(device), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                header()
                items(section.rows, key = { it.setting.id }) { row ->
                    SettingCard(row, sounds[row.setting.id], onChoice, onClear, onSound, onTest, onAddException = { addingTo = row }) { extra(row) }
                }
            }
            if (actionsStatus != null || actions.any { it.visible }) Column(
                modifier = Modifier.padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
            ) {
                actionsStatus?.invoke()
                ActionButtons(actions)
            }
        }
    }
    addingTo?.let { row ->
        AddExceptionDialog(
            candidates = row.candidates,
            onPick = { kind ->
                addingTo = null
                kind.group?.let { group -> onChoice(kind, firstExceptionChoice(group, row.choice)) }
            },
            onDismiss = { addingTo = null }
        )
    }
}

@Composable
private fun BackTopBar(title: String, onBack: () -> Unit) {
    AapsTopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreUiStrings.back))
            }
        }
    )
}

/**
 * One row as a card: its title, the choice, its sound with the Test, and for a group the kinds set apart from it, so
 * all that belongs to it is seen together. [extra] comes under the title.
 */
@Composable
private fun SettingCard(
    row: AlertSettingRow,
    sound: AlertSound?,
    onChoice: (AlertSetting, AlertChoice) -> Unit,
    onClear: (AlertKind) -> Unit,
    onSound: (AlertSetting, AlertSound?) -> Unit,
    onTest: (kind: AlertKind, vibrate: Boolean) -> Boolean,
    onAddException: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit
) {
    val setting = row.setting
    AapsCard(modifier = Modifier.fillMaxWidth()) {
        // Less at the bottom: the last line is mostly a button, whose touch area already adds space below its text
        val padding = PaddingValues(start = AapsSpacing.extraLarge, top = AapsSpacing.extraLarge, end = AapsSpacing.extraLarge, bottom = AapsSpacing.medium)
        Column(modifier = Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
            Column {
                Text(stringResource(setting.title), style = MaterialTheme.typography.titleMedium)
                if (setting is AlertGroup)
                    Text(stringResource(setting.summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // What Ring means here: an alarm until answered, or one sound
                levelLine(row.levels)?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            }
            if (row.members.isNotEmpty()) MemberLevels(row)
            extra()
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
            if (row.exceptions.isNotEmpty()) {
                HorizontalDivider()
                Text(stringResource(UiStrings.alert_kind_exceptions), style = MaterialTheme.typography.titleSmall)
                row.exceptions.forEach { ExceptionRowView(it, onChoice, onClear) }
            }
            if (row.candidates.isNotEmpty())
                TextButton(onClick = onAddException, contentPadding = LinedUpButtonPadding) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(UiStrings.alert_kind_add_exception))
                }
        }
    }
}

/**
 * A group's alerts, each with its level, behind "Its alerts": which of them ring until answered and which make one
 * sound. Closed at first, so a long list of a pump does not push the choice down.
 */
@Composable
private fun MemberLevels(row: AlertSettingRow) {
    var open by rememberSaveable(row.setting.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
        TextButton(onClick = { open = !open }, contentPadding = LinedUpButtonPadding) {
            Text(stringResource(UiStrings.alert_group_its_alerts, row.members.size))
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        if (open) row.members.forEach { kind ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                Text(stringResource(kind.title), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                levelLabel(kind.levels)?.let {
                    Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * The line under a row's title: what Ring means for it. Null for an info row, whose summary says it already, and for one
 * whose level something else sets (a BG rule shows its own).
 */
private fun levelLine(levels: Set<AlertSeverity>): TextRef? = when (levels) {
    setOf(AlertSeverity.CRITICAL) -> UiStrings.bg_alarm_rule_alarm
    setOf(AlertSeverity.WARNING)  -> UiStrings.bg_alarm_rule_warning
    setOf(AlertSeverity.INFO)     -> null
    else                          -> levelLabel(levels)
}

/** A level in one or two words: Alarm, Warning, Info, or a mix of them. Null when it is not known here. */
private fun levelLabel(levels: Set<AlertSeverity>): TextRef? = when {
    levels.isEmpty()                    -> null
    AlertSeverity.CRITICAL in levels    -> if (levels.size == 1) CoreUiStrings.alarm else UiStrings.alert_level_alarm_or_warning
    AlertSeverity.WARNING in levels     -> if (levels.size == 1) CoreUiStrings.warning else UiStrings.alert_level_warning_or_info
    else                                -> InterfacesStrings.alert_group_info
}

/** A text button with no padding at its start, so its text lines up with the text above it. */
internal val LinedUpButtonPadding =PaddingValues(top = AapsSpacing.medium, end = AapsSpacing.large, bottom = AapsSpacing.medium)

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

/** What a part holds, where its devices do not say it. */
private fun AlertSection.summary(): TextRef? = when (this) {
    AlertSection.PUMP, AlertSection.CGM -> null
    AlertSection.BG                     -> UiStrings.alert_kinds_bg_summary
    AlertSection.APP                    -> UiStrings.alert_kinds_app_summary
}
