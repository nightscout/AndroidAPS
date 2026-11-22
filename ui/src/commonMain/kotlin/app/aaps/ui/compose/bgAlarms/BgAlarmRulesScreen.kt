package app.aaps.ui.compose.bgAlarms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.alerts.BgAlarmRule
import app.aaps.core.interfaces.alerts.BgAlert
import app.aaps.core.interfaces.alerts.BgDirection
import app.aaps.core.interfaces.alerts.BgRuleType
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/** How the screen shows and reads a BG: the user's units, kept apart from the rules, which are in mg/dl. */
class BgUnits(
    /** The unit's name, for the threshold field: "mg/dl" or "mmol/l". */
    val label: String,
    /** A BG in mg/dl as the user reads it, with its unit. */
    val format: (Double) -> String,
    /** A BG in mg/dl as a number in the user's units, for the field. */
    val toUnits: (Double) -> String,
    /** A number the user typed in their units, as mg/dl, or null when it is not a number. Also for a rate. */
    val toMgdl: (String) -> Double?,
    /** A rate in mg/dl per minute as a number in the user's units: it needs more decimals than a BG in mmol/l. */
    val rateToUnits: (Double) -> String
)

/**
 * The user's own BG rules (`_docs/ALARMS.md`, steps 2a and 2c): a list, with add, edit, delete and on or off. Urgent low
 * is not here: it is always on and its threshold is in Local alerts. The rules are the master's, so on a client the
 * list is shown and cannot be changed ([readOnly]).
 *
 * Every change is saved at once ([onSave] with the whole list), as on the alert kinds screen: there is no Save button.
 *
 * On top, the pause of this phone (step 2d), also on a client: a pause is each phone's own, as its choice to ring.
 * [pauses] is kind id to the end of its pause; [timeText] shows such an end as a time of day. [onTest] plays a rule's
 * kind as it rings at the severity in its dialog, on this phone; false: not played, an alarm is ringing.
 */
@Composable
fun BgAlarmRulesScreen(
    rules: List<BgAlarmRule>,
    readOnly: Boolean,
    units: BgUnits,
    newId: () -> String,
    onSave: (List<BgAlarmRule>) -> Unit,
    pauses: Map<String, Long>,
    timeText: (Long) -> String,
    onPause: (List<BgAlert>, Int) -> Unit,
    onResume: () -> Unit,
    onTest: (BgAlert, AlertSeverity) -> Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf<BgAlarmRule?>(null) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(UiStrings.bg_alarm_rules_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreUiStrings.back))
                    }
                }
            )
        },
        // The screen's actions at the bottom, not in the top bar
        bottomBar = {
            if (!readOnly) Box(modifier = Modifier.fillMaxWidth().padding(AapsSpacing.extraLarge)) {
                Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(UiStrings.bg_alarm_rule_add))
                }
                DropdownMenu(expanded = adding, onDismissRequest = { adding = false }) {
                    RuleKind.entries.forEach { kind ->
                        DropdownMenuItem(
                            text = { Text(stringResource(kind.alert.title)) },
                            onClick = {
                                adding = false
                                editing = newRule(newId(), kind)
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            item {
                Text(
                    stringResource(if (readOnly) UiStrings.bg_alarm_rules_read_only else UiStrings.bg_alarm_rules_intro),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            item { PauseCard(pauses = pauses, timeText = timeText, onPause = onPause, onResume = onResume) }
            items(sorted(rules), key = { it.id }) { rule ->
                RuleCard(
                    rule = rule,
                    units = units,
                    readOnly = readOnly,
                    onClick = { editing = rule },
                    onEnabled = { enabled -> onSave(rules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it }) }
                )
            }
        }
    }

    editing?.let { rule ->
        RuleDialog(
            rule = rule,
            isNew = rules.none { it.id == rule.id },
            units = units,
            onTest = onTest,
            onSave = { saved ->
                onSave(if (rules.any { it.id == saved.id }) rules.map { if (it.id == saved.id) saved else it } else rules + saved)
                editing = null
            },
            onDelete = {
                onSave(rules.filterNot { it.id == rule.id })
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

/** The rules the user can add, in the order of the menu and the list. */
private enum class RuleKind(val type: BgRuleType, val direction: BgDirection, val alert: BgAlert) {

    LOW(BgRuleType.LEVEL, BgDirection.LOW, BgAlert.LOW),
    HIGH(BgRuleType.LEVEL, BgDirection.HIGH, BgAlert.HIGH),
    PERSISTENT_HIGH(BgRuleType.PERSISTENT_HIGH, BgDirection.HIGH, BgAlert.PERSISTENT_HIGH),
    FALLING(BgRuleType.RATE, BgDirection.LOW, BgAlert.FALLING_FAST),
    RISING(BgRuleType.RATE, BgDirection.HIGH, BgAlert.RISING_FAST),
    PREDICTED_LOW(BgRuleType.PREDICTED_LOW, BgDirection.LOW, BgAlert.PREDICTED_LOW);

    /** The limits a threshold must stay in, in mg/dl (a rate in mg/dl per minute). */
    val limits: ClosedFloatingPointRange<Double>
        get() = when (this) {
            LOW, PREDICTED_LOW    -> 40.0..180.0
            HIGH, PERSISTENT_HIGH -> 100.0..400.0
            FALLING, RISING       -> 0.5..10.0
        }

    /** The limits of the minutes field: how long it must last, or for predicted low how far it looks ahead. */
    val minutes: IntRange?
        get() = when (this) {
            LOW, HIGH       -> 0..240
            PERSISTENT_HIGH -> 15..240
            PREDICTED_LOW   -> 5..120
            FALLING, RISING -> null
        }
}

private fun kindOf(rule: BgAlarmRule): RuleKind = RuleKind.entries.first { it.type == rule.type && it.direction == rule.direction }

/** By kind as in the menu; in each kind the strongest first: the lowest low, the highest high, the fastest rate. */
private fun sorted(rules: List<BgAlarmRule>) = rules.sortedWith(
    compareBy<BgAlarmRule> { kindOf(it).ordinal }.thenBy { if (it.direction == BgDirection.LOW && it.type != BgRuleType.RATE) it.thresholdMgdl else -it.thresholdMgdl }
)

/** A new rule with the usual starting values. */
private fun newRule(id: String, kind: RuleKind) = BgAlarmRule(
    id = id,
    type = kind.type,
    direction = kind.direction,
    thresholdMgdl = when (kind) {
        RuleKind.LOW, RuleKind.PREDICTED_LOW -> 70.0
        RuleKind.HIGH                        -> 250.0
        RuleKind.PERSISTENT_HIGH             -> 180.0
        RuleKind.FALLING, RuleKind.RISING    -> 3.0
    },
    durationMinutes = when (kind) {
        RuleKind.PERSISTENT_HIGH -> 60
        RuleKind.PREDICTED_LOW   -> 30
        else                     -> 0
    },
    silenceMinutes = if (kind == RuleKind.HIGH || kind == RuleKind.PERSISTENT_HIGH) 120 else Alerts.SILENCE_MINUTES
)

@Composable
private fun ruleTitle(rule: BgAlarmRule, units: BgUnits): String = when (kindOf(rule)) {
    RuleKind.LOW             -> stringResource(UiStrings.bg_alarm_rule_low, units.format(rule.thresholdMgdl))
    RuleKind.HIGH            -> stringResource(UiStrings.bg_alarm_rule_high, units.format(rule.thresholdMgdl))
    RuleKind.PERSISTENT_HIGH -> stringResource(UiStrings.bg_alarm_rule_persistent_high, units.format(rule.thresholdMgdl))
    RuleKind.FALLING         -> stringResource(UiStrings.bg_alarm_rule_falling, stringResource(CoreUiStrings.value_with_unit, units.rateToUnits(rule.thresholdMgdl), units.label))
    RuleKind.RISING          -> stringResource(UiStrings.bg_alarm_rule_rising, stringResource(CoreUiStrings.value_with_unit, units.rateToUnits(rule.thresholdMgdl), units.label))
    RuleKind.PREDICTED_LOW   -> stringResource(UiStrings.bg_alarm_rule_predicted_low, units.format(rule.thresholdMgdl))
}

@Composable
private fun RuleCard(rule: BgAlarmRule, units: BgUnits, readOnly: Boolean, onClick: () -> Unit, onEnabled: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(enabled = !readOnly, onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AapsSpacing.extraLarge),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
                Text(ruleTitle(rule, units), style = MaterialTheme.typography.titleMedium)
                // Each detail its own text: the details are not one sentence a translator could order
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                    val detail = MaterialTheme.typography.bodyMedium
                    Text(stringResource(severityLabel(rule.severity)), style = detail)
                    Text(windowText(rule), style = detail)
                    when (rule.type) {
                        BgRuleType.PREDICTED_LOW -> Text(stringResource(UiStrings.bg_alarm_rule_within, rule.durationMinutes), style = detail)
                        BgRuleType.RATE          -> Unit
                        else                     -> if (rule.durationMinutes > 0) Text(stringResource(UiStrings.bg_alarm_rule_lasts, rule.durationMinutes), style = detail)
                    }
                }
            }
            Switch(checked = rule.enabled, onCheckedChange = onEnabled, enabled = !readOnly)
        }
    }
}

@Composable
private fun windowText(rule: BgAlarmRule): String {
    val from = rule.fromMinute
    val to = rule.toMinute
    return if (from == null || to == null) stringResource(UiStrings.bg_alarm_rule_all_day)
    else stringResource(UiStrings.bg_alarm_rule_window, clock(from), clock(to))
}

private fun severityLabel(severity: AlertSeverity) =
    if (severity == AlertSeverity.CRITICAL) UiStrings.bg_alarm_rule_alarm else UiStrings.bg_alarm_rule_warning

/** Minutes after midnight as "HH:MM". */
private fun clock(minute: Int): String = (minute / 60).toString().padStart(2, '0') + ":" + (minute % 60).toString().padStart(2, '0')

/** "HH:MM" as minutes after midnight, or null when it is not a time. */
private fun minuteOf(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return if (h in 0..23 && m in 0..59) h * 60 + m else null
}

@Composable
private fun RuleDialog(
    rule: BgAlarmRule,
    isNew: Boolean,
    units: BgUnits,
    onTest: (BgAlert, AlertSeverity) -> Boolean,
    onSave: (BgAlarmRule) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val kind = kindOf(rule)
    val isRate = rule.type == BgRuleType.RATE
    var threshold by remember { mutableStateOf(if (isRate) units.rateToUnits(rule.thresholdMgdl) else units.toUnits(rule.thresholdMgdl)) }
    var severity by remember { mutableStateOf(rule.severity) }
    var allDay by remember { mutableStateOf(rule.fromMinute == null || rule.toMinute == null) }
    var from by remember { mutableStateOf(clock(rule.fromMinute ?: (22 * 60))) }
    var to by remember { mutableStateOf(clock(rule.toMinute ?: (7 * 60))) }
    var duration by remember { mutableStateOf(rule.durationMinutes.toString()) }
    var silence by remember { mutableStateOf(rule.silenceMinutes) }
    var testRefused by remember { mutableStateOf(false) }

    val thresholdMgdl = units.toMgdl(threshold)?.takeIf { it in kind.limits }
    val fromMinute = minuteOf(from)
    val toMinute = minuteOf(to)
    val minutesRange = kind.minutes
    val durationMinutes = if (minutesRange == null) rule.durationMinutes else duration.trim().toIntOrNull()?.takeIf { it in minutesRange }
    val valid = thresholdMgdl != null && durationMinutes != null && (allDay || (fromMinute != null && toMinute != null && fromMinute != toMinute))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (kind) {
                    RuleKind.LOW  -> stringResource(UiStrings.bg_alarm_rule_low_title)
                    RuleKind.HIGH -> stringResource(UiStrings.bg_alarm_rule_high_title)
                    else          -> stringResource(kind.alert.title)
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                when (kind) {
                    RuleKind.PERSISTENT_HIGH          -> UiStrings.bg_alarm_rule_persistent_high_hint
                    RuleKind.FALLING, RuleKind.RISING -> UiStrings.bg_alarm_rule_rate_hint
                    RuleKind.PREDICTED_LOW            -> UiStrings.bg_alarm_rule_predicted_low_hint
                    else                              -> null
                }?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
                OutlinedTextField(
                    value = threshold,
                    onValueChange = { threshold = it },
                    label = { Text(stringResource(if (isRate) UiStrings.bg_alarm_rule_rate else UiStrings.bg_alarm_rule_threshold, units.label)) },
                    isError = thresholdMgdl == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                    listOf(AlertSeverity.WARNING, AlertSeverity.CRITICAL).forEach { s ->
                        FilterChip(selected = severity == s, onClick = { severity = s }, label = { Text(stringResource(severityLabel(s))) })
                    }
                    // As it will ring on this phone, with this kind's sound
                    TextButton(onClick = { testRefused = !onTest(kind.alert, severity) }) { Text(stringResource(UiStrings.alert_kind_sound_test)) }
                }
                if (testRefused) Text(
                    stringResource(UiStrings.alert_kind_sound_test_refused),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = allDay, onCheckedChange = { allDay = it })
                    Text(stringResource(UiStrings.bg_alarm_rule_all_day))
                }
                if (!allDay) Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                    OutlinedTextField(
                        value = from, onValueChange = { from = it }, singleLine = true, isError = fromMinute == null,
                        label = { Text(stringResource(UiStrings.bg_alarm_rule_from)) }, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = to, onValueChange = { to = it }, singleLine = true, isError = toMinute == null,
                        label = { Text(stringResource(UiStrings.bg_alarm_rule_to)) }, modifier = Modifier.weight(1f)
                    )
                }
                if (minutesRange != null) OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text(stringResource(if (kind == RuleKind.PREDICTED_LOW) UiStrings.bg_alarm_rule_look_ahead else UiStrings.bg_alarm_rule_duration)) },
                    isError = durationMinutes == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text(stringResource(CoreUiStrings.alarm_silence_length), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                    Alerts.silenceLengths(kind.alert).forEach { m ->
                        FilterChip(
                            selected = silence == m,
                            onClick = { silence = m },
                            label = {
                                Text(
                                    if (m >= 60 && m % 60 == 0) stringResource(CoreUiStrings.format_hours_only, m / 60)
                                    else stringResource(CoreUiStrings.format_mins, m)
                                )
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        rule.copy(
                            thresholdMgdl = thresholdMgdl ?: return@TextButton,
                            severity = severity,
                            fromMinute = if (allDay) null else fromMinute,
                            toMinute = if (allDay) null else toMinute,
                            durationMinutes = durationMinutes ?: return@TextButton,
                            silenceMinutes = silence
                        )
                    )
                }
            ) { Text(stringResource(CoreUiStrings.save)) }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text(stringResource(CoreUiStrings.delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(CoreUiStrings.cancel)) }
            }
        }
    )
}

/** The groups a pause takes, in the order of the chips. */
private enum class PauseGroup(val kinds: List<BgAlert>) {

    LOWS(BgAlert.LOWS),
    HIGHS(BgAlert.HIGHS),
    ALL(BgAlert.PAUSABLE)
}

/** How long a pause can last, in minutes. */
private val pauseLengths = listOf(30, 60, 120, 240)

/**
 * The pause of this phone: pick the lows, the highs or all, and how long. While paused, their alarms come as a card
 * with no sound; what is still on rings when the pause ends. Urgent low is never paused.
 */
@Composable
private fun PauseCard(pauses: Map<String, Long>, timeText: (Long) -> String, onPause: (List<BgAlert>, Int) -> Unit, onResume: () -> Unit) {
    var group by remember { mutableStateOf(PauseGroup.LOWS) }
    var minutes by remember { mutableStateOf(60) }
    val paused = BgAlert.PAUSABLE.filter { pauses[it.id] != null }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(AapsSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
            Text(stringResource(UiStrings.bg_alarm_pause_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(UiStrings.bg_alarm_pause_hint), style = MaterialTheme.typography.bodyMedium)
            // Each pause end with the kinds it holds; the kinds each their own text, not a joined sentence
            paused.groupBy { pauses.getValue(it.id) }.entries.sortedBy { it.key }.forEach { (until, kinds) ->
                Text(stringResource(UiStrings.bg_alarm_paused_until, timeText(until)), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                    kinds.forEach { Text(stringResource(it.title), style = MaterialTheme.typography.bodyMedium) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                PauseGroup.entries.forEach { g ->
                    FilterChip(
                        selected = group == g,
                        onClick = { group = g },
                        label = {
                            Text(
                                stringResource(
                                    when (g) {
                                        PauseGroup.LOWS  -> UiStrings.bg_alarm_pause_lows
                                        PauseGroup.HIGHS -> UiStrings.bg_alarm_pause_highs
                                        PauseGroup.ALL   -> UiStrings.bg_alarm_pause_all
                                    }
                                )
                            )
                        }
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                pauseLengths.forEach { m ->
                    FilterChip(
                        selected = minutes == m,
                        onClick = { minutes = m },
                        label = {
                            Text(
                                if (m >= 60 && m % 60 == 0) stringResource(CoreUiStrings.format_hours_only, m / 60)
                                else stringResource(CoreUiStrings.format_mins, m)
                            )
                        }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                Button(onClick = { onPause(group.kinds, minutes) }) { Text(stringResource(UiStrings.bg_alarm_pause)) }
                if (paused.isNotEmpty()) TextButton(onClick = onResume) { Text(stringResource(UiStrings.bg_alarm_resume)) }
            }
        }
    }
}
