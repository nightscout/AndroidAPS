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
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.compose.elements.AutomationDropdown
import app.aaps.plugins.automation.compose.elements.ComparatorConnectEditor
import app.aaps.plugins.automation.compose.elements.ComparatorExistsEditor
import app.aaps.plugins.automation.compose.elements.CompareRow
import app.aaps.plugins.automation.compose.elements.InputDateTimeEditor
import app.aaps.plugins.automation.compose.elements.InputLocationModeEditor
import app.aaps.plugins.automation.compose.elements.InputStringEditor
import app.aaps.plugins.automation.compose.elements.InputTimeEditor
import app.aaps.plugins.automation.compose.elements.InputTimeRangeEditor
import app.aaps.plugins.automation.compose.elements.InputWeekDayEditor
import app.aaps.plugins.automation.compose.elements.LabelWithElementRow
import app.aaps.plugins.automation.elements.InputBg
import app.aaps.plugins.automation.elements.InputDelta.DeltaType
import app.aaps.plugins.automation.elements.InputPercent
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
import app.aaps.core.keys.R as KeysR

@Composable
fun TriggerEditor(
    trigger: Trigger,
    onChange: () -> Unit,
    tick: Int = 0,
    bondedDevices: List<String> = emptyList(),
    showCurrentLocation: Boolean = false,
    onUseCurrentLocation: () -> Unit = {},
    onPickLocationFromMap: (TriggerLocation) -> Unit = {},
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_EXPRESSION") tick // force re-execution when sibling fields mutate
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (trigger) {
            is TriggerBg                 -> TriggerBgEditor(trigger, onChange, tick)
            is TriggerDelta              -> TriggerDeltaEditor(trigger, onChange, tick)
            is TriggerCOB                -> TriggerCOBEditor(trigger, onChange, tick)
            is TriggerIob                -> TriggerIobEditor(trigger, onChange, tick)
            is TriggerHeartRate          -> TriggerHeartRateEditor(trigger, onChange, tick)
            is TriggerAutosensValue      -> TriggerAutosensValueEditor(trigger, onChange, tick)
            is TriggerBolusAgo           -> TriggerBolusAgoEditor(trigger, onChange, tick)
            is TriggerCannulaAge         -> TriggerCannulaAgeEditor(trigger, onChange, tick)
            is TriggerInsulinAge         -> TriggerInsulinAgeEditor(trigger, onChange, tick)
            is TriggerReservoirLevel     -> TriggerReservoirLevelEditor(trigger, onChange, tick)
            is TriggerPumpBatteryAge     -> TriggerPumpBatteryAgeEditor(trigger, onChange, tick)
            is TriggerPumpBatteryLevel   -> TriggerPumpBatteryLevelEditor(trigger, onChange, tick)
            is TriggerSensorAge          -> TriggerSensorAgeEditor(trigger, onChange, tick)
            is TriggerPodChange          -> TriggerPodChangeEditor()
            is TriggerPumpLastConnection -> TriggerPumpLastConnectionEditor(trigger, onChange, tick)
            is TriggerProfilePercent     -> TriggerProfilePercentEditor(trigger, onChange, tick)
            is TriggerTempTarget         -> TriggerTempTargetEditor(trigger, onChange)
            is TriggerTempTargetValue    -> TriggerTempTargetValueEditor(trigger, onChange, tick)
            is TriggerStepsCount         -> TriggerStepsCountEditor(trigger, onChange, tick)
            is TriggerTime               -> TriggerTimeEditor(trigger, onChange)
            is TriggerRecurringTime      -> TriggerRecurringTimeEditor(trigger, onChange)
            is TriggerTimeRange          -> TriggerTimeRangeEditor(trigger, onChange)
            is TriggerWifiSsid           -> TriggerWifiSsidEditor(trigger, onChange)
            is TriggerBTDevice           -> TriggerBTDeviceEditor(trigger, bondedDevices, onChange)
            is TriggerLocation           -> TriggerLocationEditor(trigger, onChange, tick, showCurrentLocation, onUseCurrentLocation, onPickLocationFromMap)
            is TriggerConnector          -> Text("Connector")
            else                         -> Text(trigger.javaClass.simpleName)
        }
    }
}

// ---------- Leaf editors ----------

@Composable
fun TriggerBgEditor(t: TriggerBg, onChange: () -> Unit, tick: Int = 0) {
    @Suppress("UNUSED_EXPRESSION") tick
    val isMmol = t.bg.units == GlucoseUnit.MMOL
    CompareRow(
        comparator = t.comparator.value,
        onComparatorChange = { t.comparator.value = it; onChange() },
        label = ""
    ) {
        NumberInputRow(
            labelResId = 0,
            value = t.bg.value,
            onValueChange = { t.bg.value = it; onChange() },
            valueRange = if (isMmol) InputBg.MMOL_MIN..InputBg.MMOL_MAX else InputBg.MGDL_MIN..InputBg.MGDL_MAX,
            step = if (isMmol) 0.1 else 1.0,
            decimalPlaces = if (isMmol) 1 else 0,
            unitLabelResId = if (isMmol) KeysR.string.units_mmol else KeysR.string.units_mgdl,
            compact = true
        )
    }
}

@Composable
fun TriggerDeltaEditor(t: TriggerDelta, onChange: () -> Unit, tick: Int = 0) {
    @Suppress("UNUSED_EXPRESSION") tick
    val deltaTypes = DeltaType.entries
    val deltaLabels = deltaTypes.map { stringResource(it.stringRes) }
    val isMmol = t.units == GlucoseUnit.MMOL
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
        label = ""
    ) {
        NumberInputRow(
            labelResId = 0,
            value = t.delta.value,
            onValueChange = { t.delta.value = it; onChange() },
            valueRange = -72.0..72.0,
            step = 0.1,
            decimalPlaces = 1,
            unitLabelResId = if (isMmol) KeysR.string.units_mmol else KeysR.string.units_mgdl,
            compact = true
        )
    }
}

@Composable
fun TriggerCOBEditor(t: TriggerCOB, onChange: () -> Unit, tick: Int = 0) {
    @Suppress("UNUSED_EXPRESSION") tick
    CompareRow(
        comparator = t.comparator.value,
        onComparatorChange = { t.comparator.value = it; onChange() },
        label = ""
    ) {
        NumberInputRow(
            labelResId = 0,
            value = t.cob.value,
            onValueChange = { t.cob.value = it; onChange() },
            valueRange = 0.0..150.0,
            step = 1.0,
            unitLabelResId = KeysR.string.units_grams,
            compact = true
        )
    }
}

@Composable
fun TriggerIobEditor(t: TriggerIob, onChange: () -> Unit, tick: Int = 0) {
    @Suppress("UNUSED_EXPRESSION") tick
    CompareRow(
        comparator = t.comparator.value,
        onComparatorChange = { t.comparator.value = it; onChange() },
        label = ""
    ) {
        NumberInputRow(
            labelResId = 0,
            value = t.insulin.value,
            onValueChange = { t.insulin.value = it; onChange() },
            valueRange = -20.0..20.0,
            step = 0.1,
            decimalPlaces = 1,
            unitLabelResId = KeysR.string.units_insulin,
            compact = true
        )
    }
}

@Composable
fun TriggerHeartRateEditor(t: TriggerHeartRate, onChange: () -> Unit, tick: Int = 0) {
    @Suppress("UNUSED_EXPRESSION") tick
    CompareRow(
        comparator = t.comparator.value,
        onComparatorChange = { t.comparator.value = it; onChange() },
        label = ""
    ) {
        NumberInputRow(
            labelResId = 0,
            value = t.heartRate.value,
            onValueChange = { t.heartRate.value = it; onChange() },
            valueRange = 30.0..250.0,
            step = 5.0,
            unitLabelResId = R.string.automation_unit_bpm,
            compact = true
        )
    }
}

@Composable
fun TriggerAutosensValueEditor(t: TriggerAutosensValue, onChange: () -> Unit, tick: Int = 0) {
    @Suppress("UNUSED_EXPRESSION") tick
    CompareRow(
        comparator = t.comparator.value,
        onComparatorChange = { t.comparator.value = it; onChange() },
        label = ""
    ) {
        NumberInputRow(
            labelResId = 0,
            value = t.autosens.value,
            onValueChange = { t.autosens.value = it; onChange() },
            valueRange = 0.0..300.0,
            step = 1.0,
            unitLabelResId = KeysR.string.units_percent,
            compact = true
        )
    }
}

@Composable
fun TriggerBolusAgoEditor(t: TriggerBolusAgo, onChange: () -> Unit, tick: Int = 0) {
    @Suppress("UNUSED_EXPRESSION") tick
    CompareRow(
        comparator = t.comparator.value,
        onComparatorChange = { t.comparator.value = it; onChange() },
        label = ""
    ) {
        NumberInputRow(
            labelResId = 0,
            value = t.minutesAgo.value.toDouble(),
            onValueChange = { t.minutesAgo.value = it.toInt(); onChange() },
            valueRange = 5.0..(24 * 60.0),
            step = 10.0,
            unitLabelResId = KeysR.string.units_min,
            compact = true
        )
    }
}

@Composable
fun TriggerCannulaAgeEditor(t: TriggerCannulaAge, onChange: () -> Unit, tick: Int = 0) {
    @Suppress("UNUSED_EXPRESSION") tick
    CompareRow(
        comparator = t.comparator.value,
        onComparatorChange = { t.comparator.value = it; onChange() },
        label = ""
    ) {
        NumberInputRow(
            labelResId = 0,
            value = t.cannulaAgeHours.value,
            onValueChange = { t.cannulaAgeHours.value = it; onChange() },
            valueRange = 0.0..336.0,
            step = 0.1,
            decimalPlaces = 1,
            unitLabelResId = KeysR.string.units_hours,
            compact = true
        )
    }
}

@Composable
fun TriggerInsulinAgeEditor(t: TriggerInsulinAge, onChange: () -> Unit, tick: Int = 0) {
    @Suppress("UNUSED_EXPRESSION") tick
    CompareRow(
        comparator = t.comparator.value,
        onComparatorChange = { t.comparator.value = it; onChange() },
        label = ""
    ) {
        NumberInputRow(
            labelResId = 0,
            value = t.insulinAgeHours.value,
            onValueChange = { t.insulinAgeHours.value = it; onChange() },
            valueRange = 0.0..336.0,
            step = 0.1,
            decimalPlaces = 1,
            unitLabelResId = KeysR.string.units_hours,
            compact = true
        )
    }
}

@Composable
fun TriggerReservoirLevelEditor(t: TriggerReservoirLevel, onChange: () -> Unit, tick: Int = 0) {
    @Suppress("UNUSED_EXPRESSION") tick
    CompareRow(
        comparator = t.comparator.value,
        onComparatorChange = { t.comparator.value = it; onChange() },
        label = ""
    ) {
        NumberInputRow(
            labelResId = 0,
            value = t.reservoirLevel.value,
            onValueChange = { t.reservoirLevel.value = it; onChange() },
            valueRange = 0.0..800.0,
            step = 1.0,
            unitLabelResId = KeysR.string.units_insulin,
            compact = true
        )
    }
}

@Composable
fun TriggerPumpBatteryAgeEditor(t: TriggerPumpBatteryAge, onChange: () -> Unit, tick: Int = 0) {
    @Suppress("UNUSED_EXPRESSION") tick
    CompareRow(
        comparator = t.comparator.value,
        onComparatorChange = { t.comparator.value = it; onChange() },
        label = ""
    ) {
        NumberInputRow(
            labelResId = 0,
            value = t.pumpBatteryAgeHours.value,
            onValueChange = { t.pumpBatteryAgeHours.value = it; onChange() },
            valueRange = 0.0..336.0,
            step = 0.1,
            decimalPlaces = 1,
            unitLabelResId = KeysR.string.units_hours,
            compact = true
        )
    }
}

@Composable
fun TriggerPumpBatteryLevelEditor(t: TriggerPumpBatteryLevel, onChange: () -> Unit, tick: Int = 0) {
    @Suppress("UNUSED_EXPRESSION") tick
    CompareRow(
        comparator = t.comparator.value,
        onComparatorChange = { t.comparator.value = it; onChange() },
        label = ""
    ) {
        NumberInputRow(
            labelResId = 0,
            value = t.pumpBatteryLevel.value,
            onValueChange = { t.pumpBatteryLevel.value = it; onChange() },
            valueRange = 0.0..100.0,
            step = 1.0,
            unitLabelResId = KeysR.string.units_percent,
            compact = true
        )
    }
}

@Composable
fun TriggerSensorAgeEditor(t: TriggerSensorAge, onChange: () -> Unit, tick: Int = 0) {
    @Suppress("UNUSED_EXPRESSION") tick
    CompareRow(
        comparator = t.comparator.value,
        onComparatorChange = { t.comparator.value = it; onChange() },
        label = ""
    ) {
        NumberInputRow(
            labelResId = 0,
            value = t.sensorAgeHours.value,
            onValueChange = { t.sensorAgeHours.value = it; onChange() },
            valueRange = 0.0..720.0,
            step = 0.1,
            decimalPlaces = 1,
            unitLabelResId = KeysR.string.units_hours,
            compact = true
        )
    }
}

@Composable
fun TriggerPodChangeEditor() {
    Text(stringResource(R.string.triggerPodChangeDesc))
}

@Composable
fun TriggerPumpLastConnectionEditor(t: TriggerPumpLastConnection, onChange: () -> Unit, tick: Int = 0) {
    @Suppress("UNUSED_EXPRESSION") tick
    CompareRow(
        comparator = t.comparator.value,
        onComparatorChange = { t.comparator.value = it; onChange() },
        label = ""
    ) {
        NumberInputRow(
            labelResId = 0,
            value = t.minutesAgo.value.toDouble(),
            onValueChange = { t.minutesAgo.value = it.toInt(); onChange() },
            valueRange = 5.0..(24 * 60.0),
            step = 10.0,
            unitLabelResId = KeysR.string.units_min,
            compact = true
        )
    }
}

@Composable
fun TriggerProfilePercentEditor(t: TriggerProfilePercent, onChange: () -> Unit, tick: Int = 0) {
    @Suppress("UNUSED_EXPRESSION") tick
    CompareRow(
        comparator = t.comparator.value,
        onComparatorChange = { t.comparator.value = it; onChange() },
        label = ""
    ) {
        NumberInputRow(
            labelResId = 0,
            value = t.pct.value,
            onValueChange = { t.pct.value = it; onChange() },
            valueRange = InputPercent.MIN..InputPercent.MAX,
            step = 5.0,
            unitLabelResId = KeysR.string.units_percent,
            compact = true
        )
    }
}

@Composable
fun TriggerTempTargetEditor(t: TriggerTempTarget, onChange: () -> Unit) {
    ComparatorExistsEditor(t.comparator.value, onValueChange = { t.comparator.value = it; onChange() })
}

@Composable
fun TriggerTempTargetValueEditor(t: TriggerTempTargetValue, onChange: () -> Unit, tick: Int = 0) {
    @Suppress("UNUSED_EXPRESSION") tick
    val isMmol = t.ttValue.units == GlucoseUnit.MMOL
    CompareRow(
        comparator = t.comparator.value,
        onComparatorChange = { t.comparator.value = it; onChange() },
        label = ""
    ) {
        NumberInputRow(
            labelResId = 0,
            value = t.ttValue.value,
            onValueChange = { t.ttValue.value = it; onChange() },
            valueRange = if (isMmol) Constants.MIN_TT_MMOL..Constants.MAX_TT_MMOL
            else Constants.MIN_TT_MGDL..Constants.MAX_TT_MGDL,
            step = if (isMmol) 0.1 else 1.0,
            decimalPlaces = if (isMmol) 1 else 0,
            unitLabelResId = if (isMmol) KeysR.string.units_mmol else KeysR.string.units_mgdl,
            compact = true
        )
    }
}

@Composable
fun TriggerStepsCountEditor(t: TriggerStepsCount, onChange: () -> Unit, tick: Int = 0) {
    @Suppress("UNUSED_EXPRESSION") tick
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
        NumberInputRow(
            labelResId = 0,
            value = t.stepsCount.value,
            onValueChange = { t.stepsCount.value = it; onChange() },
            valueRange = 0.0..20000.0,
            step = 10.0,
            compact = true
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
    tick: Int = 0,
    showCurrentLocation: Boolean,
    onUseCurrentLocation: () -> Unit,
    onPickLocationFromMap: (TriggerLocation) -> Unit
) {
    @Suppress("UNUSED_EXPRESSION") tick
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
    NumberInputRow(
        labelResId = 0,
        value = t.distance.value,
        onValueChange = { t.distance.value = it; onChange() },
        valueRange = 0.0..100000.0,
        step = 10.0,
        unitLabelResId = R.string.automation_unit_meters
    )
    InputLocationModeEditor(
        value = t.modeSelected.value,
        onValueChange = { t.modeSelected.value = it; onChange() }
    )
}
