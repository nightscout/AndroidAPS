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
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.Scene
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.actions.Action
import app.aaps.plugins.automation.actions.ActionAlarm
import app.aaps.plugins.automation.actions.ActionCarePortalEvent
import app.aaps.plugins.automation.actions.ActionDisableScene
import app.aaps.plugins.automation.actions.ActionEnableScene
import app.aaps.plugins.automation.actions.ActionNotification
import app.aaps.plugins.automation.actions.ActionProfileSwitch
import app.aaps.plugins.automation.actions.ActionProfileSwitchPercent
import app.aaps.plugins.automation.actions.ActionRunAutotune
import app.aaps.plugins.automation.actions.ActionRunScene
import app.aaps.plugins.automation.actions.ActionSMBChange
import app.aaps.plugins.automation.actions.ActionSendSMS
import app.aaps.plugins.automation.actions.ActionSettingsExport
import app.aaps.plugins.automation.actions.ActionStartTempTarget
import app.aaps.plugins.automation.compose.elements.AutomationDropdown
import app.aaps.plugins.automation.compose.elements.InputDropdownOnOffEditor
import app.aaps.plugins.automation.compose.elements.InputStringEditor
import app.aaps.plugins.automation.compose.elements.InputWeekDayEditor
import app.aaps.plugins.automation.compose.elements.LabelWithElementRow
import app.aaps.plugins.automation.elements.InputPercent
import app.aaps.core.keys.R as KeysR

@Composable
fun ActionEditor(
    action: Action,
    profileNames: List<String>,
    sceneOptions: List<Scene>,
    tick: Int = 0,
    onChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_EXPRESSION") tick
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
            is ActionCarePortalEvent      -> ActionCarePortalEventEditor(action, tick, onChange)
            is ActionSMBChange            -> ActionSMBChangeEditor(action, onChange)
            is ActionProfileSwitch        -> ActionProfileSwitchEditor(action, profileNames, onChange)
            is ActionProfileSwitchPercent -> ActionProfileSwitchPercentEditor(action, tick, onChange)
            is ActionRunAutotune          -> ActionRunAutotuneEditor(action, profileNames, tick, onChange)
            is ActionStartTempTarget      -> ActionStartTempTargetEditor(action, tick, onChange)
            is ActionRunScene             -> ActionRunSceneEditor(action, sceneOptions, onChange)
            is ActionEnableScene          -> ActionEnableSceneEditor(action, sceneOptions, onChange)
            is ActionDisableScene         -> ActionDisableSceneEditor(action, sceneOptions, onChange)
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
fun ActionCarePortalEventEditor(a: ActionCarePortalEvent, tick: Int = 0, onChange: () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") tick
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
    NumberInputRow(
        labelResId = app.aaps.core.ui.R.string.duration_label,
        value = a.duration.value.toDouble(),
        onValueChange = { a.duration.value = it.toInt(); onChange() },
        valueRange = 5.0..(24 * 60.0),
        step = 10.0,
        unitLabelResId = KeysR.string.units_min
    )
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
fun ActionProfileSwitchPercentEditor(a: ActionProfileSwitchPercent, tick: Int = 0, onChange: () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") tick
    NumberInputRow(
        labelResId = app.aaps.core.ui.R.string.percent,
        value = a.pct.value,
        onValueChange = { a.pct.value = it; onChange() },
        valueRange = InputPercent.MIN..InputPercent.MAX,
        step = 5.0,
        unitLabelResId = KeysR.string.units_percent
    )
    NumberInputRow(
        labelResId = app.aaps.core.ui.R.string.duration_label,
        value = a.duration.value.toDouble(),
        onValueChange = { a.duration.value = it.toInt(); onChange() },
        valueRange = 5.0..(24 * 60.0),
        step = 10.0,
        unitLabelResId = KeysR.string.units_min
    )
}

@Composable
fun ActionRunAutotuneEditor(
    a: ActionRunAutotune,
    profileNames: List<String>,
    tick: Int = 0,
    onChange: () -> Unit
) {
    @Suppress("UNUSED_EXPRESSION") tick
    AutomationDropdown(
        value = a.profileNameOrEmpty().ifEmpty { profileNames.firstOrNull() ?: "" },
        options = profileNames,
        onValueChange = { a.setProfileName(it); onChange() },
        label = stringResource(app.aaps.core.ui.R.string.autotune_select_profile)
    )
    NumberInputRow(
        labelResId = app.aaps.core.ui.R.string.autotune_tune_days,
        value = a.daysBackRef().value.toDouble(),
        onValueChange = { a.daysBackRef().value = it.toInt(); onChange() },
        valueRange = 1.0..30.0,
        step = 1.0,
        unitLabelResId = KeysR.string.units_days
    )
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
fun ActionStartTempTargetEditor(a: ActionStartTempTarget, tick: Int = 0, onChange: () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") tick
    val isMmol = a.value.units == GlucoseUnit.MMOL
    NumberInputRow(
        labelResId = app.aaps.core.ui.R.string.temporary_target,
        value = a.value.value,
        onValueChange = { a.value.value = it; onChange() },
        valueRange = if (isMmol) Constants.MIN_TT_MMOL..Constants.MAX_TT_MMOL
        else Constants.MIN_TT_MGDL..Constants.MAX_TT_MGDL,
        step = if (isMmol) 0.1 else 1.0,
        decimalPlaces = if (isMmol) 1 else 0,
        unitLabelResId = if (isMmol) KeysR.string.units_mmol else KeysR.string.units_mgdl
    )
    NumberInputRow(
        labelResId = app.aaps.core.ui.R.string.duration_label,
        value = a.duration.value.toDouble(),
        onValueChange = { a.duration.value = it.toInt(); onChange() },
        valueRange = 5.0..(24 * 60.0),
        step = 10.0,
        unitLabelResId = KeysR.string.units_min
    )
}

@Composable
private fun ScenePicker(
    selectedId: String,
    sceneOptions: List<Scene>,
    onPicked: (String) -> Unit
) {
    // Disambiguate dropdown labels when scene names collide so picking is unambiguous.
    val nameCounts = sceneOptions.groupingBy { it.name }.eachCount()
    val labelById = sceneOptions.associate { scene ->
        val unique = (nameCounts[scene.name] ?: 0) <= 1
        scene.id to if (unique) scene.name else "${scene.name} (${scene.id.take(6)})"
    }
    val labels = sceneOptions.map { labelById.getValue(it.id) }
    val selectedLabel = labelById[selectedId].orEmpty()
    AutomationDropdown(
        value = selectedLabel,
        options = labels,
        onValueChange = { pickedLabel ->
            labelById.entries.firstOrNull { it.value == pickedLabel }?.key?.let(onPicked)
        },
        label = stringResource(R.string.action_scene_label)
    )
}

@Composable
fun ActionRunSceneEditor(a: ActionRunScene, sceneOptions: List<Scene>, onChange: () -> Unit) {
    ScenePicker(
        selectedId = a.scene.value,
        sceneOptions = sceneOptions,
        onPicked = { a.scene.value = it; onChange() }
    )
}

@Composable
fun ActionEnableSceneEditor(a: ActionEnableScene, sceneOptions: List<Scene>, onChange: () -> Unit) {
    ScenePicker(
        selectedId = a.scene.value,
        sceneOptions = sceneOptions,
        onPicked = { a.scene.value = it; onChange() }
    )
}

@Composable
fun ActionDisableSceneEditor(a: ActionDisableScene, sceneOptions: List<Scene>, onChange: () -> Unit) {
    ScenePicker(
        selectedId = a.scene.value,
        sceneOptions = sceneOptions,
        onPicked = { a.scene.value = it; onChange() }
    )
}
