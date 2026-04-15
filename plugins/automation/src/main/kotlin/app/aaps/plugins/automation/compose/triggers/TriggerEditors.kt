package app.aaps.plugins.automation.compose.triggers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.compose.elements.AutomationDropdown
import app.aaps.plugins.automation.compose.elements.ComparatorConnectEditor
import app.aaps.plugins.automation.compose.elements.ComparatorExistsEditor
import app.aaps.plugins.automation.compose.elements.CompareRow
import app.aaps.plugins.automation.compose.elements.InputBgEditor
import app.aaps.plugins.automation.compose.elements.InputDateTimeEditor
import app.aaps.plugins.automation.compose.elements.InputDoubleEditor
import app.aaps.plugins.automation.compose.elements.InputDurationEditor
import app.aaps.plugins.automation.compose.elements.InputInsulinEditor
import app.aaps.plugins.automation.compose.elements.InputLocationModeEditor
import app.aaps.plugins.automation.compose.elements.InputPercentEditor
import app.aaps.plugins.automation.compose.elements.InputStringEditor
import app.aaps.plugins.automation.compose.elements.InputTempTargetEditor
import app.aaps.plugins.automation.compose.elements.InputTimeEditor
import app.aaps.plugins.automation.compose.elements.InputTimeRangeEditor
import app.aaps.plugins.automation.compose.elements.InputWeekDayEditor
import app.aaps.plugins.automation.compose.elements.LabelWithElementRow
import app.aaps.plugins.automation.elements.InputDelta.DeltaType
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.plugins.automation.triggers.TriggerAutosensValue
import app.aaps.plugins.automation.triggers.TriggerBTDevice
import app.aaps.plugins.automation.triggers.TriggerBg
import app.aaps.plugins.automation.triggers.TriggerBolusAgo
import app.aaps.plugins.automation.triggers.TriggerCOB
import app.aaps.plugins.automation.triggers.TriggerCannulaAge
import app.aaps.plugins.automation.triggers.TriggerConnector
import app.aaps.plugins.automation.triggers.TriggerDelta
import app.aaps.plugins.automation.triggers.TriggerHeartRate
import app.aaps.plugins.automation.triggers.TriggerInsulinAge
import app.aaps.plugins.automation.triggers.TriggerIob
import app.aaps.plugins.automation.triggers.TriggerLocation
import app.aaps.plugins.automation.triggers.TriggerPodChange
import app.aaps.plugins.automation.triggers.TriggerProfilePercent
import app.aaps.plugins.automation.triggers.TriggerPumpBatteryAge
import app.aaps.plugins.automation.triggers.TriggerPumpBatteryLevel
import app.aaps.plugins.automation.triggers.TriggerPumpLastConnection
import app.aaps.plugins.automation.triggers.TriggerRecurringTime
import app.aaps.plugins.automation.triggers.TriggerReservoirLevel
import app.aaps.plugins.automation.triggers.TriggerSensorAge
import app.aaps.plugins.automation.triggers.TriggerStepsCount
import app.aaps.plugins.automation.triggers.TriggerTempTarget
import app.aaps.plugins.automation.triggers.TriggerTempTargetValue
import app.aaps.plugins.automation.triggers.TriggerTime
import app.aaps.plugins.automation.triggers.TriggerTimeRange
import app.aaps.plugins.automation.triggers.TriggerWifiSsid
import java.text.DecimalFormat

@Composable
fun TriggerEditor(
    trigger: Trigger,
    onChange: () -> Unit,
    bondedDevices: List<String> = emptyList(),
    showCurrentLocation: Boolean = false,
    onUseCurrentLocation: () -> Unit = {},
    onPickLocationFromMap: (TriggerLocation) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (trigger) {
            is TriggerBg                 -> TriggerBgEditor(trigger, onChange)
            is TriggerDelta              -> TriggerDeltaEditor(trigger, onChange)
            is TriggerCOB                -> TriggerCOBEditor(trigger, onChange)
            is TriggerIob                -> TriggerIobEditor(trigger, onChange)
            is TriggerHeartRate          -> TriggerHeartRateEditor(trigger, onChange)
            is TriggerAutosensValue      -> TriggerAutosensValueEditor(trigger, onChange)
            is TriggerBolusAgo           -> TriggerBolusAgoEditor(trigger, onChange)
            is TriggerCannulaAge         -> TriggerCannulaAgeEditor(trigger, onChange)
            is TriggerInsulinAge         -> TriggerInsulinAgeEditor(trigger, onChange)
            is TriggerReservoirLevel     -> TriggerReservoirLevelEditor(trigger, onChange)
            is TriggerPumpBatteryAge     -> TriggerPumpBatteryAgeEditor(trigger, onChange)
            is TriggerPumpBatteryLevel   -> TriggerPumpBatteryLevelEditor(trigger, onChange)
            is TriggerSensorAge          -> TriggerSensorAgeEditor(trigger, onChange)
            is TriggerPodChange          -> TriggerPodChangeEditor()
            is TriggerPumpLastConnection -> TriggerPumpLastConnectionEditor(trigger, onChange)
            is TriggerProfilePercent     -> TriggerProfilePercentEditor(trigger, onChange)
            is TriggerTempTarget         -> TriggerTempTargetEditor(trigger, onChange)
            is TriggerTempTargetValue    -> TriggerTempTargetValueEditor(trigger, onChange)
            is TriggerStepsCount         -> TriggerStepsCountEditor(trigger, onChange)
            is TriggerTime               -> TriggerTimeEditor(trigger, onChange)
            is TriggerRecurringTime      -> TriggerRecurringTimeEditor(trigger, onChange)
            is TriggerTimeRange          -> TriggerTimeRangeEditor(trigger, onChange)
            is TriggerWifiSsid           -> TriggerWifiSsidEditor(trigger, onChange)
            is TriggerBTDevice           -> TriggerBTDeviceEditor(trigger, bondedDevices, onChange)
            is TriggerLocation           -> TriggerLocationEditor(trigger, onChange, showCurrentLocation, onUseCurrentLocation, onPickLocationFromMap)
            is TriggerConnector          -> Text("Connector")
            else                         -> Text(trigger.javaClass.simpleName)
        }
    }
}

// ---------- Leaf editors ----------

@Composable
fun TriggerBgEditor(t: TriggerBg, onChange: () -> Unit) {
    CompareRow(
        comparator = t.comparator.value,
        onComparatorChange = { t.comparator.value = it; onChange() },
        label = "",
        suffix = if (t.bg.units == app.aaps.core.data.model.GlucoseUnit.MMOL) "mmol/L" else "mg/dL"
    ) {
        InputBgEditor(t.bg, onValueChange = { t.bg.value = it; onChange() })
    }
}

@Composable
fun TriggerDeltaEditor(t: TriggerDelta, onChange: () -> Unit) {
    val deltaTypes = DeltaType.entries
    val deltaLabels = deltaTypes.map { stringResource(it.stringRes) }
    AutomationDropdown(
        value = deltaLabels[deltaTypes.indexOf(t.delta.deltaType)],
        options = deltaLabels,
        onValueChange = { picked ->
            val idx = deltaLabels.indexOf(picked)
            if (idx in deltaTypes.indices) {
                t.delta.deltaType = deltaTypes[idx]; onChange()
            }
        }
    )
    CompareRow(
        comparator = t.comparator.value,
        onComparatorChange = { t.comparator.value = it; onChange() },
        label = "",
        suffix = if (t.units == app.aaps.core.data.model.GlucoseUnit.MMOL) "mmol/L" else "mg/dL"
    ) {
        InputDoubleEditor(
            value = t.delta.value,
            onValueChange = { t.delta.value = it; onChange() },
            range = -72.0..72.0,
            step = 0.1,
            format = DecimalFormat("0.0")
        )
    }
}

@Composable
fun TriggerCOBEditor(t: TriggerCOB, onChange: () -> Unit) = CompareRow(
    comparator = t.comparator.value,
    onComparatorChange = { t.comparator.value = it; onChange() },
    label = "",
    suffix = "g"
) {
    InputDoubleEditor(t.cob.value, { t.cob.value = it; onChange() }, 0.0..150.0, 1.0)
}

@Composable
fun TriggerIobEditor(t: TriggerIob, onChange: () -> Unit) = CompareRow(
    comparator = t.comparator.value,
    onComparatorChange = { t.comparator.value = it; onChange() },
    label = "",
    suffix = "U"
) {
    InputInsulinEditor(t.insulin.value, { t.insulin.value = it; onChange() })
}

@Composable
fun TriggerHeartRateEditor(t: TriggerHeartRate, onChange: () -> Unit) = CompareRow(
    comparator = t.comparator.value,
    onComparatorChange = { t.comparator.value = it; onChange() },
    label = "",
    suffix = "bpm"
) {
    InputDoubleEditor(t.heartRate.value, { t.heartRate.value = it; onChange() }, 30.0..250.0, 5.0, DecimalFormat("0"))
}

@Composable
fun TriggerAutosensValueEditor(t: TriggerAutosensValue, onChange: () -> Unit) = CompareRow(
    comparator = t.comparator.value,
    onComparatorChange = { t.comparator.value = it; onChange() },
    label = "",
    suffix = "%"
) {
    InputDoubleEditor(t.autosens.value, { t.autosens.value = it; onChange() }, 0.0..300.0, 1.0)
}

@Composable
fun TriggerBolusAgoEditor(t: TriggerBolusAgo, onChange: () -> Unit) = CompareRow(
    comparator = t.comparator.value,
    onComparatorChange = { t.comparator.value = it; onChange() },
    label = ""
) {
    InputDurationEditor(t.minutesAgo, onChange = { t.minutesAgo.value = it; onChange() })
}

@Composable
fun TriggerCannulaAgeEditor(t: TriggerCannulaAge, onChange: () -> Unit) = CompareRow(
    comparator = t.comparator.value,
    onComparatorChange = { t.comparator.value = it; onChange() },
    label = "",
    suffix = "h"
) {
    InputDoubleEditor(t.cannulaAgeHours.value, { t.cannulaAgeHours.value = it; onChange() }, 0.0..336.0, 0.1, DecimalFormat("0.0"))
}

@Composable
fun TriggerInsulinAgeEditor(t: TriggerInsulinAge, onChange: () -> Unit) = CompareRow(
    comparator = t.comparator.value,
    onComparatorChange = { t.comparator.value = it; onChange() },
    label = "",
    suffix = "h"
) {
    InputDoubleEditor(t.insulinAgeHours.value, { t.insulinAgeHours.value = it; onChange() }, 0.0..336.0, 0.1, DecimalFormat("0.0"))
}

@Composable
fun TriggerReservoirLevelEditor(t: TriggerReservoirLevel, onChange: () -> Unit) = CompareRow(
    comparator = t.comparator.value,
    onComparatorChange = { t.comparator.value = it; onChange() },
    label = "",
    suffix = "U"
) {
    InputDoubleEditor(t.reservoirLevel.value, { t.reservoirLevel.value = it; onChange() }, 0.0..800.0, 1.0, DecimalFormat("0"))
}

@Composable
fun TriggerPumpBatteryAgeEditor(t: TriggerPumpBatteryAge, onChange: () -> Unit) = CompareRow(
    comparator = t.comparator.value,
    onComparatorChange = { t.comparator.value = it; onChange() },
    label = "",
    suffix = "h"
) {
    InputDoubleEditor(t.pumpBatteryAgeHours.value, { t.pumpBatteryAgeHours.value = it; onChange() }, 0.0..336.0, 0.1, DecimalFormat("0.0"))
}

@Composable
fun TriggerPumpBatteryLevelEditor(t: TriggerPumpBatteryLevel, onChange: () -> Unit) = CompareRow(
    comparator = t.comparator.value,
    onComparatorChange = { t.comparator.value = it; onChange() },
    label = "",
    suffix = "%"
) {
    InputDoubleEditor(t.pumpBatteryLevel.value, { t.pumpBatteryLevel.value = it; onChange() }, 0.0..100.0, 1.0, DecimalFormat("0"))
}

@Composable
fun TriggerSensorAgeEditor(t: TriggerSensorAge, onChange: () -> Unit) = CompareRow(
    comparator = t.comparator.value,
    onComparatorChange = { t.comparator.value = it; onChange() },
    label = "",
    suffix = "h"
) {
    InputDoubleEditor(t.sensorAgeHours.value, { t.sensorAgeHours.value = it; onChange() }, 0.0..720.0, 0.1, DecimalFormat("0.0"))
}

@Composable
fun TriggerPodChangeEditor() {
    Text(stringResource(R.string.triggerPodChangeDesc))
}

@Composable
fun TriggerPumpLastConnectionEditor(t: TriggerPumpLastConnection, onChange: () -> Unit) = CompareRow(
    comparator = t.comparator.value,
    onComparatorChange = { t.comparator.value = it; onChange() },
    label = ""
) {
    InputDurationEditor(t.minutesAgo, onChange = { t.minutesAgo.value = it; onChange() })
}

@Composable
fun TriggerProfilePercentEditor(t: TriggerProfilePercent, onChange: () -> Unit) = CompareRow(
    comparator = t.comparator.value,
    onComparatorChange = { t.comparator.value = it; onChange() },
    label = "",
    suffix = "%"
) {
    InputPercentEditor(t.pct.value, { t.pct.value = it; onChange() })
}

@Composable
fun TriggerTempTargetEditor(t: TriggerTempTarget, onChange: () -> Unit) {
    ComparatorExistsEditor(t.comparator.value, onValueChange = { t.comparator.value = it; onChange() })
}

@Composable
fun TriggerTempTargetValueEditor(t: TriggerTempTargetValue, onChange: () -> Unit) = CompareRow(
    comparator = t.comparator.value,
    onComparatorChange = { t.comparator.value = it; onChange() },
    label = "",
    suffix = if (t.ttValue.units == app.aaps.core.data.model.GlucoseUnit.MMOL) "mmol/L" else "mg/dL"
) {
    InputTempTargetEditor(
        value = t.ttValue.value,
        units = t.ttValue.units,
        onValueChange = { t.ttValue.value = it; onChange() }
    )
}

@Composable
fun TriggerStepsCountEditor(t: TriggerStepsCount, onChange: () -> Unit) {
    val durations = listOf("5", "10", "15", "30", "60", "180")
    LabelWithElementRow(
        textPre = stringResource(R.string.triggerStepsCountDropdownLabel) + ":",
        textPost = stringResource(app.aaps.core.interfaces.R.string.unit_minutes)
    ) {
        AutomationDropdown(
            value = t.measurementDuration.value.ifEmpty { "5" },
            options = durations,
            onValueChange = { t.measurementDuration.setValue(it); onChange() }
        )
    }
    CompareRow(
        comparator = t.comparator.value,
        onComparatorChange = { t.comparator.value = it; onChange() },
        label = ""
    ) {
        InputDoubleEditor(
            value = t.stepsCount.value,
            onValueChange = { t.stepsCount.value = it; onChange() },
            range = 0.0..20000.0, step = 10.0, format = DecimalFormat("0")
        )
    }
}

@Composable
fun TriggerTimeEditor(t: TriggerTime, onChange: () -> Unit) {
    InputDateTimeEditor(timeMillis = t.time.value, onChange = { t.time.value = it; onChange() })
}

@Composable
fun TriggerRecurringTimeEditor(t: TriggerRecurringTime, onChange: () -> Unit) {
    InputWeekDayEditor(weekdays = t.days, onChange = onChange)
    InputTimeEditor(
        minutesSinceMidnight = t.time.value,
        onChange = { t.time.value = it; onChange() }
    )
}

@Composable
fun TriggerTimeRangeEditor(t: TriggerTimeRange, onChange: () -> Unit) {
    InputTimeRangeEditor(
        startMinutes = t.range.start,
        endMinutes = t.range.end,
        onChangeStart = { t.range.start = it; onChange() },
        onChangeEnd = { t.range.end = it; onChange() }
    )
}

@Composable
fun TriggerWifiSsidEditor(t: TriggerWifiSsid, onChange: () -> Unit) = CompareRow(
    comparator = t.comparator.value,
    onComparatorChange = { t.comparator.value = it; onChange() },
    label = ""
) {
    InputStringEditor(value = t.ssid.value, onValueChange = { t.ssid.value = it; onChange() })
}

@Composable
fun TriggerBTDeviceEditor(
    t: TriggerBTDevice,
    bondedDevices: List<String>,
    onChange: () -> Unit
) {
    AutomationDropdown(
        value = t.btDevice.value.ifEmpty { bondedDevices.firstOrNull() ?: "" },
        options = bondedDevices,
        onValueChange = { t.btDevice.setValue(it); onChange() }
    )
    ComparatorConnectEditor(t.comparator.value, onValueChange = { t.comparator.value = it; onChange() })
}

@Composable
fun TriggerLocationEditor(
    t: TriggerLocation,
    onChange: () -> Unit,
    showCurrentLocation: Boolean,
    onUseCurrentLocation: () -> Unit,
    onPickLocationFromMap: (TriggerLocation) -> Unit
) {
    InputStringEditor(
        value = t.name.value, onValueChange = { t.name.value = it; onChange() },
        label = stringResource(app.aaps.core.ui.R.string.name_short)
    )
    if (showCurrentLocation) {
        OutlinedButton(onClick = onUseCurrentLocation, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.currentlocation))
        }
    }
    OutlinedButton(onClick = { onPickLocationFromMap(t) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.pick_from_map))
    }
    InputDoubleEditor(
        value = t.distance.value,
        onValueChange = { t.distance.value = it; onChange() },
        range = 0.0..100000.0, step = 10.0, format = DecimalFormat("0"), suffix = "m"
    )
    InputLocationModeEditor(
        value = t.modeSelected.value,
        onValueChange = { t.modeSelected.value = it; onChange() }
    )
}
