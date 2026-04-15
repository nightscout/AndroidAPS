package app.aaps.plugins.automation.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.plugins.automation.triggers.TriggerAutosensValue
import app.aaps.plugins.automation.triggers.TriggerBTDevice
import app.aaps.plugins.automation.triggers.TriggerBg
import app.aaps.plugins.automation.triggers.TriggerBolusAgo
import app.aaps.plugins.automation.triggers.TriggerCOB
import app.aaps.plugins.automation.triggers.TriggerCannulaAge
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

enum class TriggerCategory(val labelResId: Int) {
    Time(R.string.automation_category_time),
    Glucose(R.string.automation_category_glucose),
    Device(R.string.automation_category_device),
    Sensors(R.string.automation_category_sensors),
    Other(R.string.automation_category_other)
}

fun triggerCategoryOf(triggerClass: Class<*>): TriggerCategory = when (triggerClass) {
    TriggerTime::class.java,
    TriggerRecurringTime::class.java,
    TriggerTimeRange::class.java -> TriggerCategory.Time

    TriggerBg::class.java,
    TriggerDelta::class.java,
    TriggerCOB::class.java,
    TriggerIob::class.java,
    TriggerAutosensValue::class.java,
    TriggerProfilePercent::class.java,
    TriggerTempTarget::class.java,
    TriggerTempTargetValue::class.java,
    TriggerBolusAgo::class.java -> TriggerCategory.Glucose

    TriggerCannulaAge::class.java,
    TriggerInsulinAge::class.java,
    TriggerReservoirLevel::class.java,
    TriggerPumpBatteryAge::class.java,
    TriggerPumpBatteryLevel::class.java,
    TriggerSensorAge::class.java,
    TriggerPodChange::class.java,
    TriggerPumpLastConnection::class.java -> TriggerCategory.Device

    TriggerHeartRate::class.java,
    TriggerStepsCount::class.java,
    TriggerLocation::class.java,
    TriggerWifiSsid::class.java,
    TriggerBTDevice::class.java -> TriggerCategory.Sensors

    else -> TriggerCategory.Other
}

data class TriggerOption(
    val className: String,
    val labelResId: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector?,
    val iconTint: androidx.compose.ui.graphics.Color?,
    val category: TriggerCategory
) {

    companion object {

        fun from(trigger: Trigger): TriggerOption = TriggerOption(
            className = trigger.javaClass.name,
            labelResId = trigger.friendlyName(),
            icon = trigger.composeIcon(),
            iconTint = trigger.composeIconTint(),
            category = triggerCategoryOf(trigger.javaClass)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChooseTriggerSheet(
    options: List<TriggerOption>,
    onPick: (TriggerOption) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.automation_choose_trigger),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(16.dp))
            val grouped = options.groupBy { it.category }
            TriggerCategory.entries.forEach { cat ->
                val items = grouped[cat].orEmpty()
                if (items.isEmpty()) return@forEach
                Text(
                    text = stringResource(cat.labelResId),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items.forEach { opt ->
                        AssistChip(
                            onClick = {
                                onPick(opt)
                                onDismiss()
                            },
                            label = { Text(stringResource(opt.labelResId)) },
                            leadingIcon = opt.icon?.let {
                                {
                                    Icon(
                                        imageVector = it,
                                        contentDescription = null,
                                        tint = opt.iconTint ?: MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
