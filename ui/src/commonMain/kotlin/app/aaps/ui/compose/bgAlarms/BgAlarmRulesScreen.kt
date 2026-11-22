package app.aaps.ui.compose.bgAlarms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
    /** A number the user typed in their units, as mg/dl, or null when it is not a number. */
    val toMgdl: (String) -> Double?
)

/**
 * The user's own BG level rules (`_docs/ALARMS.md`, step 2a): a list, with add, edit, delete and on or off. Urgent low
 * is not here: it is always on and its threshold is in Local alerts. The rules are the master's, so on a client the
 * list is shown and cannot be changed ([readOnly]).
 *
 * Every change is saved at once ([onSave] with the whole list), as on the alert kinds screen: there is no Save button.
 */
@Composable
fun BgAlarmRulesScreen(
    rules: List<BgAlarmRule>,
    readOnly: Boolean,
    units: BgUnits,
    newId: () -> String,
    onSave: (List<BgAlarmRule>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf<BgAlarmRule?>(null) }

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
            if (!readOnly) Row(
                modifier = Modifier.fillMaxWidth().padding(AapsSpacing.extraLarge),
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
            ) {
                Button(onClick = { editing = newRule(newId(), BgDirection.LOW) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(UiStrings.bg_alarm_rule_add_low))
                }
                Button(onClick = { editing = newRule(newId(), BgDirection.HIGH) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(UiStrings.bg_alarm_rule_add_high))
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

/** The lows first, the strongest first; then the highs, the strongest first. */
private fun sorted(rules: List<BgAlarmRule>) =
    rules.filter { it.direction == BgDirection.LOW }.sortedBy { it.thresholdMgdl } +
        rules.filter { it.direction == BgDirection.HIGH }.sortedByDescending { it.thresholdMgdl }

/** A new rule with the usual starting values: 70 mg/dl for a low, 250 for a high. */
private fun newRule(id: String, direction: BgDirection) = BgAlarmRule(
    id = id,
    direction = direction,
    thresholdMgdl = if (direction == BgDirection.LOW) 70.0 else 250.0,
    silenceMinutes = if (direction == BgDirection.LOW) Alerts.SILENCE_MINUTES else 120
)

@Composable
private fun RuleCard(rule: BgAlarmRule, units: BgUnits, readOnly: Boolean, onClick: () -> Unit, onEnabled: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(enabled = !readOnly, onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AapsSpacing.extraLarge),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
                Text(
                    stringResource(
                        if (rule.direction == BgDirection.LOW) UiStrings.bg_alarm_rule_low else UiStrings.bg_alarm_rule_high,
                        units.format(rule.thresholdMgdl)
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
                // Each detail its own text: the details are not one sentence a translator could order
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                    val detail = MaterialTheme.typography.bodyMedium
                    Text(stringResource(severityLabel(rule.severity)), style = detail)
                    Text(windowText(rule), style = detail)
                    if (rule.durationMinutes > 0) Text(stringResource(UiStrings.bg_alarm_rule_lasts, rule.durationMinutes), style = detail)
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

/** The limits a threshold must stay in, in mg/dl: a low rule below the range, a high one above it. */
private fun BgDirection.limits() = if (this == BgDirection.LOW) 40.0..180.0 else 100.0..400.0

@Composable
private fun RuleDialog(
    rule: BgAlarmRule,
    isNew: Boolean,
    units: BgUnits,
    onSave: (BgAlarmRule) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var threshold by remember { mutableStateOf(units.toUnits(rule.thresholdMgdl)) }
    var severity by remember { mutableStateOf(rule.severity) }
    var allDay by remember { mutableStateOf(rule.fromMinute == null || rule.toMinute == null) }
    var from by remember { mutableStateOf(clock(rule.fromMinute ?: (22 * 60))) }
    var to by remember { mutableStateOf(clock(rule.toMinute ?: (7 * 60))) }
    var duration by remember { mutableStateOf(rule.durationMinutes.toString()) }
    var silence by remember { mutableStateOf(rule.silenceMinutes) }

    val thresholdMgdl = units.toMgdl(threshold)?.takeIf { it in rule.direction.limits() }
    val fromMinute = minuteOf(from)
    val toMinute = minuteOf(to)
    val durationMinutes = duration.trim().toIntOrNull()?.takeIf { it in 0..240 }
    val valid = thresholdMgdl != null && durationMinutes != null && (allDay || (fromMinute != null && toMinute != null && fromMinute != toMinute))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (rule.direction == BgDirection.LOW) UiStrings.bg_alarm_rule_low_title else UiStrings.bg_alarm_rule_high_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                OutlinedTextField(
                    value = threshold,
                    onValueChange = { threshold = it },
                    label = { Text(stringResource(UiStrings.bg_alarm_rule_threshold, units.label)) },
                    isError = thresholdMgdl == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                    listOf(AlertSeverity.WARNING, AlertSeverity.CRITICAL).forEach { s ->
                        FilterChip(selected = severity == s, onClick = { severity = s }, label = { Text(stringResource(severityLabel(s))) })
                    }
                }
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
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text(stringResource(UiStrings.bg_alarm_rule_duration)) },
                    isError = durationMinutes == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text(stringResource(CoreUiStrings.alarm_silence_length), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                    Alerts.silenceLengths(BgAlert.LOW).forEach { m ->
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
