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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import app.aaps.core.interfaces.alerts.AlertChoice
import app.aaps.core.interfaces.alerts.AlertGroup
import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertSetting
import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.AlertSound
import app.aaps.core.interfaces.alerts.BgAlarmRule
import app.aaps.core.interfaces.alerts.BgAlarmRules
import app.aaps.core.interfaces.alerts.BgAlert
import app.aaps.core.interfaces.alerts.BgDirection
import app.aaps.core.interfaces.alerts.BgRuleType
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.ScreenAction
import app.aaps.core.ui.compose.TimeRangePicker
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.activities.durationText
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
 * The pause of this phone (step 2d), also on a client: a pause is each phone's own, as its choice to ring. Only the one
 * planned ahead is here, "Pause all for…" at the bottom with Reset, as the actions of the pump and sync screens, and
 * while it lasts "Paused until" over them, its button then Resume (decided 2026-11-27); a ringing alarm is silenced from
 * its own screen, one tap (decided 2026-11-26). [pauses] is kind id to the end of its pause; [timeText]
 * shows such an end as a time of day. [onRuleTest] plays a rule's kind as it rings at the severity in its dialog, on
 * this phone; false: not played, an alarm is ringing.
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
    onResume: (List<BgAlert>) -> Unit,
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
    var resetting by remember { mutableStateOf(false) }
    var pausing by remember { mutableStateOf(false) }
    val pausedUntil = BgAlert.PAUSABLE.mapNotNull { pauses[it.id] }.maxOrNull()

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
        },
        // The screen's actions, pinned at the bottom as on the pump and sync screens (decided 2026-11-27). The pause is
        // this phone's own, also on a client. Reset only where the rules may be changed, always there so it can be
        // found (hidden while the rules were the default ones, nobody knew it was there), greyed when it would change
        // nothing.
        actions = listOf(
            if (pausedUntil == null) ScreenAction(label = stringResource(UiStrings.bg_alarms_pause_all_for), icon = Icons.Filled.Pause, onClick = { pausing = true })
            else ScreenAction(label = stringResource(UiStrings.bg_alarm_resume), icon = Icons.Filled.PlayArrow, onClick = { onResume(BgAlert.PAUSABLE) }),
            ScreenAction(
                label = stringResource(UiStrings.bg_alarms_reset),
                icon = Icons.Filled.Refresh,
                enabled = rules != BgAlarmRules.DEFAULT,
                visible = !readOnly,
                onClick = { resetting = true }
            )
        ),
        // While a pause lasts, also one given from a ringing alarm, it is said right over its Resume: seen wherever the
        // list is scrolled to (on top of the list it was out of sight after pausing from the bottom)
        actionsStatus = pausedUntil?.let { until -> { Paused(timeText(until)) } }
    )

    if (pausing) PauseDialog(
        onPick = { minutes ->
            pausing = false
            onPause(BgAlert.PAUSABLE, minutes)
        },
        onDismiss = { pausing = false }
    )

    // It replaces every rule, on every phone: it says what comes instead and asks first
    if (resetting) AlertDialog(
        onDismissRequest = { resetting = false },
        title = { Text(stringResource(UiStrings.bg_alarms_reset)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                Text(stringResource(UiStrings.bg_alarms_reset_text), style = MaterialTheme.typography.bodyMedium)
                BgAlarmRules.DEFAULT.forEach { rule ->
                    Column {
                        Text(ruleTitle(rule, units), style = MaterialTheme.typography.bodyLarge)
                        RuleDetails(rule)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(BgAlarmRules.DEFAULT)
                resetting = false
            }) { Text(stringResource(CoreUiStrings.reset)) }
        },
        dismissButton = { TextButton(onClick = { resetting = false }) { Text(stringResource(CoreUiStrings.cancel)) } }
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
        // Said, not only shown red: a value out of it gives no other hint why Save does nothing
        hint = stringResource(UiStrings.bg_alarms_allowed_range, units.format(URGENT_LOW_LIMITS.start), units.format(URGENT_LOW_LIMITS.endInclusive)),
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
        hint = stringResource(
            UiStrings.bg_alarms_allowed_range,
            stringResource(CoreUiStrings.format_mins, staleMinutesRange.first),
            stringResource(CoreUiStrings.format_mins, staleMinutesRange.last)
        ),
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
 * The rules of one group, the same on every phone: each with its checkbox, its details and a pencil, a tap opens it. Then "Add
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

/** A number for an alarm: [parse] gives the value, or null when the text is not one it may be; [hint] says which may. */
@Composable
private fun NumberDialog(
    title: String,
    label: String,
    hint: String,
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
                supportingText = { Text(hint) },
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

    /**
     * The limits of the minutes field: for persistent high how long it must last, for predicted low how far it looks
     * ahead. None for a low or a high, which ring at once (decided 2026-11-27), nor for a rate.
     */
    val minutes: IntRange?
        get() = when (this) {
            PERSISTENT_HIGH            -> BgAlarmRule.PERSISTENT_HIGH_MIN_MINUTES..240
            PREDICTED_LOW              -> 5..120
            LOW, HIGH, FALLING, RISING -> null
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
    }
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

/**
 * One rule in its group's card, as a rule of Automation and a scene: its checkbox to turn it on and off first, then its
 * title and details, and the pencil that says a tap opens it, as the urgent low row above (decided 2026-11-27: with a
 * switch only, it did not look as if it could be opened). Read only: no pencil, and the checkbox cannot be changed.
 */
@Composable
private fun RuleRow(rule: BgAlarmRule, units: BgUnits, readOnly: Boolean, onClick: () -> Unit, onEnabled: (Boolean) -> Unit) {
    val title = ruleTitle(rule, units)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !readOnly, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Named, or a screen reader says only "checkbox"
        Checkbox(checked = rule.enabled, onCheckedChange = onEnabled, enabled = !readOnly, modifier = Modifier.semantics { contentDescription = title })
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            RuleDetails(rule)
        }
        if (!readOnly) Icon(Icons.Filled.Edit, contentDescription = stringResource(CoreUiStrings.change))
    }
}

/** How a rule rings, when, and for persistent high how long it waits, for predicted low how far it looks ahead. */
@Composable
private fun RuleDetails(rule: BgAlarmRule) {
    // Each detail its own text: the details are not one sentence a translator could order
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
        val detail = MaterialTheme.typography.bodyMedium
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Text(stringResource(severityLabel(rule.severity)), style = detail, color = color)
        Text(windowText(rule), style = detail, color = color)
        when (rule.type) {
            BgRuleType.PREDICTED_LOW          -> Text(stringResource(UiStrings.bg_alarm_rule_within, rule.durationMinutes), style = detail, color = color)
            BgRuleType.PERSISTENT_HIGH        -> Text(stringResource(UiStrings.bg_alarm_rule_lasts, rule.durationMinutes), style = detail, color = color)
            BgRuleType.LEVEL, BgRuleType.RATE -> Unit
        }
    }
}

/** The time of day a rule applies, in the phone's time format, as the picker of its dialog shows it. */
@Composable
private fun windowText(rule: BgAlarmRule): String {
    val from = rule.fromMinute
    val to = rule.toMinute
    if (from == null || to == null) return stringResource(UiStrings.bg_alarm_rule_all_day)
    val dateUtil = LocalDateUtil.current
    val clock = { minute: Int -> dateUtil.timeString(dateUtil.secondsOfTheDayToMillisecondsOfHoursAndMinutes(minute * 60)) }
    return stringResource(UiStrings.bg_alarm_rule_window, clock(from), clock(to))
}

private fun severityLabel(severity: AlertSeverity) =
    if (severity == AlertSeverity.CRITICAL) UiStrings.bg_alarm_rule_alarm else UiStrings.bg_alarm_rule_warning

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
    var fromMinute by remember { mutableIntStateOf(rule.fromMinute ?: (22 * 60)) }
    var toMinute by remember { mutableIntStateOf(rule.toMinute ?: (7 * 60)) }
    var duration by remember { mutableStateOf(rule.durationMinutes.toString()) }
    var testRefused by remember { mutableStateOf(false) }

    val thresholdMgdl = units.toMgdl(threshold)?.takeIf { it in kind.limits }
    val minutesRange = kind.minutes
    val durationMinutes = if (minutesRange == null) rule.durationMinutes else duration.trim().toIntOrNull()?.takeIf { it in minutesRange }
    // From and To at the same time would be a window of no time at all
    val valid = thresholdMgdl != null && durationMinutes != null && (allDay || fromMinute != toMinute)

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
                // The words answer a tap too, not only the small box
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = allDay, role = Role.Checkbox, onValueChange = { allDay = it })
                        .padding(vertical = AapsSpacing.medium)
                ) {
                    Checkbox(checked = allDay, onCheckedChange = null)
                    Text(stringResource(UiStrings.bg_alarm_rule_all_day))
                }
                // A tap on a time opens the clock, as for the valid hours of QuickWizard
                if (!allDay) TimeRangePicker(
                    label = null,
                    startSeconds = fromMinute * 60,
                    endSeconds = toMinute * 60,
                    onStartChange = { fromMinute = it / 60 },
                    onEndChange = { toMinute = it / 60 },
                    modifier = Modifier.fillMaxWidth()
                )
                if (minutesRange != null) OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text(stringResource(if (kind == RuleKind.PREDICTED_LOW) UiStrings.bg_alarm_rule_look_ahead else UiStrings.bg_alarm_rule_duration)) },
                    isError = durationMinutes == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                // How long it is silenced is answered on the alarm itself, not decided here (decided 2026-11-26)
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
                            durationMinutes = durationMinutes ?: return@TextButton
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

/** How long a pause can last, in minutes. */
private val pauseLengths = listOf(30, 60, 120, 240)

/**
 * While a pause of this phone lasts, also one given from a ringing alarm ("Silence all BG alarms"): until when, over
 * Resume. How a pause works is said in its dialog, where it is chosen.
 */
@Composable
private fun Paused(until: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
        Icon(Icons.Filled.Pause, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(UiStrings.bg_alarm_paused_until, until), style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * "Pause all for…", planned ahead, as before sport: the lows, the highs and missed readings for a while, never urgent
 * low. How a pause works is said here, where it is chosen, and one tap on a length pauses, as on the alarm screen
 * (decided 2026-11-27). Snoozing one alarm is done on the alarm itself.
 */
@Composable
private fun PauseDialog(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(UiStrings.bg_alarms_pause_all_for)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                Text(stringResource(UiStrings.bg_alarm_pause_hint), style = MaterialTheme.typography.bodyMedium)
                // Two in a row: four do not fit with a large font
                pauseLengths.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                        pair.forEach { minutes ->
                            FilledTonalButton(onClick = { onPick(minutes) }, modifier = Modifier.weight(1f)) { Text(durationText(minutes), maxLines = 1) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiStrings.cancel)) } }
    )
}
