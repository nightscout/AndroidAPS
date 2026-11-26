package app.aaps.ui.compose.bgAlarms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import app.aaps.core.interfaces.alerts.AlertChoice
import app.aaps.core.interfaces.alerts.AlertGroup
import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertSetting
import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.AlertSound
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.alerts.BgAlarmRule
import app.aaps.core.interfaces.alerts.BgAlert
import app.aaps.core.interfaces.alerts.BgDirection
import app.aaps.core.interfaces.alerts.BgRuleType
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.alertKinds.AlertSectionScreen
import app.aaps.ui.compose.alertKinds.AlertSettingsSection
import app.aaps.ui.compose.alertKinds.LinedUpButtonPadding

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
 * The BG alarms, all on one screen (decided 2026-11-26): one card for each alarm, with when it rings and how this phone
 * rings it side by side.
 *
 * - **Urgent low**: its threshold ([urgentLowMgdl]), the same on every phone; it always rings.
 * - **Lows** and **Highs**: the user's own rules of the group (`_docs/ALARMS.md`, steps 2a and 2c), with add, edit,
 *   delete and on or off, the same on every phone; then how this phone rings them.
 * - **Missed BG readings**: after how long without a reading ([staleMinutes], this phone's own), and how it rings. Off
 *   unless the user turns it on.
 *
 * The rules and the urgent low threshold are the master's, so on a client they are shown and cannot be changed
 * ([readOnly]); how this phone rings can always be changed. The cards and the choices are those of the other parts of
 * the alert settings ([AlertSectionScreen] with the BG part, [section]).
 *
 * Every change is saved at once ([onSave] with the whole list of rules): there is no Save button.
 *
 * On top, the pause of this phone (step 2d), also on a client: a pause is each phone's own, as its choice to ring.
 * [pauses] is kind id to the end of its pause; [timeText] shows such an end as a time of day. [onRuleTest] plays a
 * rule's kind as it rings at the severity in its dialog, on this phone; false: not played, an alarm is ringing.
 */
@Composable
fun BgAlarmsScreen(
    section: AlertSettingsSection,
    rules: List<BgAlarmRule>,
    readOnly: Boolean,
    units: BgUnits,
    newId: () -> String,
    onSave: (List<BgAlarmRule>) -> Unit,
    urgentLowMgdl: Double,
    onUrgentLow: (Double) -> Unit,
    staleMinutes: Int,
    /** What [staleMinutes] may be, or null where it is not changed here: in simple mode it keeps its default. */
    staleMinutesRange: IntRange?,
    onStaleMinutes: (Int) -> Unit,
    pauses: Map<String, Long>,
    timeText: (Long) -> String,
    onPause: (List<BgAlert>, Int) -> Unit,
    onResume: () -> Unit,
    onRuleTest: (BgAlert, AlertSeverity) -> Boolean,
    onChoice: (AlertSetting, AlertChoice) -> Unit,
    onClear: (AlertKind) -> Unit,
    sounds: Map<String, AlertSound>,
    onSound: (AlertSetting, AlertSound?) -> Unit,
    onTest: (kind: AlertKind, vibrate: Boolean) -> Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    var editing by remember { mutableStateOf<BgAlarmRule?>(null) }
    var editingUrgentLow by remember { mutableStateOf(false) }
    var editingStale by remember { mutableStateOf(false) }

    AlertSectionScreen(
        section = section,
        onChoice = onChoice,
        onClear = onClear,
        sounds = sounds,
        onSound = onSound,
        onTest = onTest,
        onBack = onBack,
        modifier = modifier,
        listState = listState,
        header = {
            if (readOnly) item(key = "readOnly") { Text(stringResource(UiStrings.bg_alarms_read_only), style = MaterialTheme.typography.bodyMedium) }
            item(key = "pause") { PauseCard(pauses = pauses, timeText = timeText, onPause = onPause, onResume = onResume) }
        },
        extra = { row ->
            when (row.setting) {
                BgAlert.URGENT_LOW  -> {
                    SharedValue(
                        text = stringResource(UiStrings.bg_alarms_urgent_low_below, units.format(urgentLowMgdl)),
                        note = stringResource(UiStrings.bg_alarms_same_on_every_phone),
                        onEdit = { editingUrgentLow = true }.takeIf { !readOnly }
                    )
                    OnThisPhone()
                }

                AlertGroup.BG_LOWS  -> RuleList(RuleKind.LOWS, rules, units, readOnly, onEdit = { editing = it }, onAdd = { editing = newRule(newId(), it) }) { rule, enabled ->
                    onSave(rules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it })
                }

                AlertGroup.BG_HIGHS -> RuleList(RuleKind.HIGHS, rules, units, readOnly, onEdit = { editing = it }, onAdd = { editing = newRule(newId(), it) }) { rule, enabled ->
                    onSave(rules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it })
                }

                // All of it is this phone's own: no line between what is shared and what is not
                BgAlert.STALE       -> SharedValue(
                    text = stringResource(UiStrings.bg_alarms_stale_after, staleMinutes),
                    note = null,
                    onEdit = { editingStale = true }.takeIf { staleMinutesRange != null }
                )

                else                -> Unit
            }
        }
    )

    editing?.let { rule ->
        RuleDialog(
            rule = rule,
            isNew = rules.none { it.id == rule.id },
            units = units,
            onTest = onRuleTest,
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
    if (editingUrgentLow) NumberDialog(
        title = stringResource(BgAlert.URGENT_LOW.title),
        label = stringResource(UiStrings.bg_alarm_rule_threshold, units.label),
        initial = units.toUnits(urgentLowMgdl),
        decimal = true,
        parse = { text -> units.toMgdl(text)?.takeIf { it in URGENT_LOW_LIMITS } },
        onSave = { mgdl ->
            onUrgentLow(mgdl)
            editingUrgentLow = false
        },
        onDismiss = { editingUrgentLow = false }
    )
    if (editingStale && staleMinutesRange != null) NumberDialog(
        title = stringResource(BgAlert.STALE.title),
        label = stringResource(UiStrings.bg_alarms_stale_minutes),
        initial = staleMinutes.toString(),
        decimal = false,
        parse = { text -> text.trim().toIntOrNull()?.takeIf { it in staleMinutesRange }?.toDouble() },
        onSave = { minutes ->
            onStaleMinutes(minutes.toInt())
            editingStale = false
        },
        onDismiss = { editingStale = false }
    )
}

/** The limits of the urgent low threshold in mg/dl: those of its setting. */
private val URGENT_LOW_LIMITS = UnitDoubleKey.AlertUrgentLow.minMgdl.toDouble()..UnitDoubleKey.AlertUrgentLow.maxMgdl.toDouble()

/** The line that parts what is set for every phone from how this phone rings. */
@Composable
private fun OnThisPhone() {
    HorizontalDivider()
    Text(stringResource(UiStrings.bg_alarms_on_this_phone), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A value of an alarm, with a [note] under it; [onEdit] null: it cannot be changed here. */
@Composable
private fun SharedValue(text: String, note: String?, onEdit: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = onEdit != null) { onEdit?.invoke() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (onEdit != null) Icon(Icons.Filled.Edit, contentDescription = stringResource(CoreUiStrings.change))
    }
}

/**
 * The rules of one group, the same on every phone: each with its details and its switch, a tap opens it. Then "Add
 * rule" with the kinds of the group, and the line to how this phone rings them.
 */
@Composable
private fun RuleList(
    kinds: List<RuleKind>,
    rules: List<BgAlarmRule>,
    units: BgUnits,
    readOnly: Boolean,
    onEdit: (BgAlarmRule) -> Unit,
    onAdd: (RuleKind) -> Unit,
    onEnabled: (BgAlarmRule, Boolean) -> Unit
) {
    var adding by remember { mutableStateOf(false) }
    val own = sorted(rules.filter { kindOf(it) in kinds })
    Text(stringResource(UiStrings.bg_alarms_rules_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // A choice to ring with no rule rings nothing: say so, or Ring looks like an alarm that is set
    if (own.isEmpty()) Text(stringResource(UiStrings.bg_alarms_no_rules), style = MaterialTheme.typography.bodyMedium)
    own.forEach { rule -> RuleRow(rule, units, readOnly, onClick = { onEdit(rule) }, onEnabled = { onEnabled(rule, it) }) }
    if (!readOnly) Box {
        TextButton(onClick = { adding = true }, contentPadding = LinedUpButtonPadding) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(UiStrings.bg_alarm_rule_add))
        }
        DropdownMenu(expanded = adding, onDismissRequest = { adding = false }) {
            kinds.forEach { kind ->
                DropdownMenuItem(
                    text = { Text(stringResource(kind.alert.title)) },
                    onClick = {
                        adding = false
                        onAdd(kind)
                    }
                )
            }
        }
    }
    OnThisPhone()
}

/** A number for an alarm: [parse] gives the value, or null when the text is not one it may be. */
@Composable
private fun NumberDialog(
    title: String,
    label: String,
    initial: String,
    decimal: Boolean,
    parse: (String) -> Double?,
    onSave: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    val value = parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                isError = value == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number)
            )
        },
        confirmButton = { TextButton(enabled = value != null, onClick = { value?.let(onSave) }) { Text(stringResource(CoreUiStrings.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiStrings.cancel)) } }
    )
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

    companion object {

        /** The rules of the group Lows, in the order of their menu. */
        val LOWS: List<RuleKind> get() = entries.filter { it.alert in BgAlert.LOWS }

        /** The rules of the group Highs. */
        val HIGHS: List<RuleKind> get() = entries.filter { it.alert in BgAlert.HIGHS }
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

/** One rule in its group's card: its title and details, and its switch. A tap opens it. */
@Composable
private fun RuleRow(rule: BgAlarmRule, units: BgUnits, readOnly: Boolean, onClick: () -> Unit, onEnabled: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !readOnly, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall)) {
            Text(ruleTitle(rule, units), style = MaterialTheme.typography.bodyLarge)
            // Each detail its own text: the details are not one sentence a translator could order
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                val detail = MaterialTheme.typography.bodyMedium
                val color = MaterialTheme.colorScheme.onSurfaceVariant
                Text(stringResource(severityLabel(rule.severity)), style = detail, color = color)
                Text(windowText(rule), style = detail, color = color)
                when (rule.type) {
                    BgRuleType.PREDICTED_LOW -> Text(stringResource(UiStrings.bg_alarm_rule_within, rule.durationMinutes), style = detail, color = color)
                    BgRuleType.RATE          -> Unit
                    else                     -> if (rule.durationMinutes > 0) Text(stringResource(UiStrings.bg_alarm_rule_lasts, rule.durationMinutes), style = detail, color = color)
                }
            }
        }
        Switch(checked = rule.enabled, onCheckedChange = onEnabled, enabled = !readOnly)
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
    AapsCard(modifier = Modifier.fillMaxWidth()) {
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
