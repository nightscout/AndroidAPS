package app.aaps.plugins.automation.compose.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.actions.Action
import app.aaps.plugins.automation.actions.ActionAlarm
import app.aaps.plugins.automation.actions.ActionCarePortalEvent
import app.aaps.plugins.automation.actions.ActionNotification
import app.aaps.plugins.automation.actions.ActionProfileSwitch
import app.aaps.plugins.automation.actions.ActionProfileSwitchPercent
import app.aaps.plugins.automation.actions.ActionRunAutotune
import app.aaps.plugins.automation.actions.ActionSMBChange
import app.aaps.plugins.automation.actions.ActionSendSMS
import app.aaps.plugins.automation.actions.ActionSettingsExport
import app.aaps.plugins.automation.actions.ActionStartTempTarget
import app.aaps.plugins.automation.compose.elements.AutomationDropdown
import app.aaps.plugins.automation.compose.elements.InputDropdownOnOffEditor
import app.aaps.plugins.automation.compose.elements.InputDurationEditor
import app.aaps.plugins.automation.compose.elements.InputPercentEditor
import app.aaps.plugins.automation.compose.elements.InputStringEditor
import app.aaps.plugins.automation.compose.elements.InputTempTargetEditor
import app.aaps.plugins.automation.compose.elements.InputWeekDayEditor
import app.aaps.plugins.automation.compose.elements.LabelWithElementRow

@Composable
fun ActionEditor(
    action: Action,
    profileNames: List<String>,
    onChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!action.hasDialog()) {
        // Actions with no user input — show a short description.
        Text(
            text = staticDescription(action),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.fillMaxWidth()
        )
        return
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when (action) {
            is ActionAlarm                -> ActionAlarmEditor(action, onChange)
            is ActionNotification         -> ActionNotificationEditor(action, onChange)
            is ActionSendSMS              -> ActionSendSMSEditor(action, onChange)
            is ActionSettingsExport       -> ActionSettingsExportEditor(action, onChange)
            is ActionCarePortalEvent      -> ActionCarePortalEventEditor(action, onChange)
            is ActionSMBChange            -> ActionSMBChangeEditor(action, onChange)
            is ActionProfileSwitch        -> ActionProfileSwitchEditor(action, profileNames, onChange)
            is ActionProfileSwitchPercent -> ActionProfileSwitchPercentEditor(action, onChange)
            is ActionRunAutotune          -> ActionRunAutotuneEditor(action, profileNames, onChange)
            is ActionStartTempTarget      -> ActionStartTempTargetEditor(action, onChange)
            else                          -> Text(action.javaClass.simpleName)
        }
    }
}

@Composable
private fun staticDescription(action: Action): String = action.shortDescription().ifEmpty { action.javaClass.simpleName }

// ---------- Individual editors ----------

@Composable
fun ActionAlarmEditor(a: ActionAlarm, onChange: () -> Unit) {
    InputStringEditor(
        value = a.text.value,
        onValueChange = { a.text.value = it; onChange() },
        label = stringResource(R.string.alarm_short)
    )
}

@Composable
fun ActionNotificationEditor(a: ActionNotification, onChange: () -> Unit) {
    InputStringEditor(
        value = a.text.value,
        onValueChange = { a.text.value = it; onChange() },
        label = stringResource(R.string.message_short)
    )
}

@Composable
fun ActionSendSMSEditor(a: ActionSendSMS, onChange: () -> Unit) {
    InputStringEditor(
        value = a.text.value,
        onValueChange = { a.text.value = it; onChange() },
        label = stringResource(R.string.sendsmsactiontext)
    )
}

@Composable
fun ActionSettingsExportEditor(a: ActionSettingsExport, onChange: () -> Unit) {
    val textField = a.textFieldRef()
    InputStringEditor(
        value = textField.value,
        onValueChange = { textField.value = it; onChange() },
        label = stringResource(R.string.export_settings_short)
    )
}

private fun ActionSettingsExport.textFieldRef(): app.aaps.plugins.automation.elements.InputString =
    javaClass.getDeclaredField("text").let {
        it.isAccessible = true
        it.get(this) as app.aaps.plugins.automation.elements.InputString
    }

@Composable
fun ActionCarePortalEventEditor(a: ActionCarePortalEvent, onChange: () -> Unit) {
    val options = app.aaps.plugins.automation.elements.InputCarePortalMenu.EventType.entries
    val labels = options.map { stringResource(it.stringRes) }
    AutomationDropdown(
        value = labels[options.indexOf(a.cpEvent.value)],
        options = labels,
        onValueChange = { picked ->
            val idx = labels.indexOf(picked)
            if (idx in options.indices) {
                a.cpEvent.value = options[idx]; onChange()
            }
        }
    )
    LabelWithElementRow(textPre = stringResource(app.aaps.core.ui.R.string.duration_min_label)) {
        InputDurationEditor(a.duration, onChange = { a.duration.value = it; onChange() })
    }
    InputStringEditor(
        value = a.note.value,
        onValueChange = { a.note.value = it; onChange() },
        label = stringResource(app.aaps.core.ui.R.string.notes_label)
    )
}

@Composable
fun ActionSMBChangeEditor(a: ActionSMBChange, onChange: () -> Unit) {
    LabelWithElementRow(textPre = stringResource(R.string.newSmbMode)) {
        InputDropdownOnOffEditor(
            on = a.smbState.value,
            onValueChange = { a.smbState.setValue(it); onChange() }
        )
    }
}

@Composable
fun ActionProfileSwitchEditor(
    a: ActionProfileSwitch,
    profileNames: List<String>,
    onChange: () -> Unit
) {
    // Show the stored value only if it still exists in the profile list;
    // otherwise render an empty field so the user must explicitly pick one.
    val displayValue = if (a.inputProfileName.value in profileNames) a.inputProfileName.value else ""
    AutomationDropdown(
        value = displayValue,
        options = profileNames,
        onValueChange = { a.inputProfileName.value = it; onChange() },
        label = stringResource(R.string.profilename)
    )
}

@Composable
fun ActionProfileSwitchPercentEditor(a: ActionProfileSwitchPercent, onChange: () -> Unit) {
    LabelWithElementRow(textPre = stringResource(R.string.percent_u)) {
        InputPercentEditor(value = a.pct.value, onValueChange = { a.pct.value = it; onChange() })
    }
    LabelWithElementRow(textPre = stringResource(app.aaps.core.ui.R.string.duration_min_label)) {
        InputDurationEditor(a.duration, onChange = { a.duration.value = it; onChange() })
    }
}

@Composable
fun ActionRunAutotuneEditor(
    a: ActionRunAutotune,
    profileNames: List<String>,
    onChange: () -> Unit
) {
    AutomationDropdown(
        value = a.profileNameOrEmpty().ifEmpty { profileNames.firstOrNull() ?: "" },
        options = profileNames,
        onValueChange = { a.setProfileName(it); onChange() },
        label = stringResource(app.aaps.core.ui.R.string.autotune_select_profile)
    )
    LabelWithElementRow(textPre = stringResource(app.aaps.core.ui.R.string.autotune_tune_days)) {
        InputDurationEditor(a.daysBackRef(), onChange = { a.daysBackRef().value = it; onChange() })
    }
    InputWeekDayEditor(weekdays = a.daysRef(), onChange = onChange)
}

// Read-only accessors for ActionRunAutotune private fields via reflection-free shims
private fun ActionRunAutotune.profileNameOrEmpty(): String = javaClass.getDeclaredField("inputProfileName").let {
    it.isAccessible = true; (it.get(this) as app.aaps.plugins.automation.elements.InputProfileName).value
}

private fun ActionRunAutotune.setProfileName(v: String) {
    javaClass.getDeclaredField("inputProfileName").let {
        it.isAccessible = true
        (it.get(this) as app.aaps.plugins.automation.elements.InputProfileName).value = v
    }
}

private fun ActionRunAutotune.daysBackRef(): app.aaps.plugins.automation.elements.InputDuration =
    javaClass.getDeclaredField("daysBack").let {
        it.isAccessible = true
        it.get(this) as app.aaps.plugins.automation.elements.InputDuration
    }

private fun ActionRunAutotune.daysRef(): app.aaps.core.ui.elements.WeekDay =
    javaClass.getDeclaredField("days").let {
        it.isAccessible = true
        it.get(this) as app.aaps.core.ui.elements.WeekDay
    }

@Composable
fun ActionStartTempTargetEditor(a: ActionStartTempTarget, onChange: () -> Unit) {
    LabelWithElementRow(
        textPre = stringResource(app.aaps.core.ui.R.string.temporary_target)
    ) {
        InputTempTargetEditor(
            value = a.value.value,
            units = a.value.units,
            onValueChange = { a.value.value = it; onChange() }
        )
    }
    LabelWithElementRow(textPre = stringResource(app.aaps.core.ui.R.string.duration_min_label)) {
        InputDurationEditor(a.duration, onChange = { a.duration.value = it; onChange() })
    }
}
