package app.aaps.ui.compose.quickWizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Watch
import app.aaps.core.ui.compose.icons.IcBolus
import app.aaps.core.ui.compose.icons.IcCarbs
import app.aaps.core.ui.compose.icons.IcQuickwizard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.objects.wizard.QuickWizardMode
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.TimeRangePicker
import app.aaps.ui.R
import app.aaps.ui.compose.quickWizard.viewmodels.TrendOption
import app.aaps.core.keys.R as KeysR
import app.aaps.core.ui.R as CoreR

/**
 * Editor for QuickWizard entry with all configurable fields.
 * Displays all settings inline for the selected entry.
 *
 * @param buttonText Button text for this entry
 * @param carbs Carbs amount
 * @param carbTime Carb timing offset in minutes
 * @param validFrom Valid from time (seconds from midnight)
 * @param validTo Valid to time (seconds from midnight)
 * @param useBG Use blood glucose in calculation
 * @param useCOB Use carbs on board
 * @param useIOB Use insulin on board
 * @param usePositiveIOBOnly Use only positive IOB
 * @param useTrend Use trend in calculation
 * @param useSuperBolus Use super bolus
 * @param useTempTarget Use temp target
 * @param useAlarm Enable alarm for pre-bolus
 * @param percentage Bolus percentage
 * @param devicePhone Show on phone
 * @param deviceWatch Show on watch
 * @param useEcarbs Enable extended carbs
 * @param time eCarbs time offset in minutes
 * @param duration eCarbs duration in hours
 * @param carbs2 eCarbs additional carbs amount
 * @param showSuperBolusOption Whether superbolus feature is enabled
 * @param showWearOptions Whether wear control is enabled
 * @param maxCarbs Maximum allowed carbs
 * @param dateUtil For time formatting
 * @param rh Resource helper
 * @param onButtonTextChange Callback when button text changes
 * @param onCarbsChange Callback when carbs change
 * @param onCarbTimeChange Callback when carb time changes
 * @param onValidFromChange Callback when valid from changes
 * @param onValidToChange Callback when valid to changes
 * @param onUseBGChange Callback when useBG changes
 * @param onUseCOBChange Callback when useCOB changes
 * @param onUseIOBChange Callback when useIOB changes
 * @param onUsePositiveIOBOnlyChange Callback when usePositiveIOBOnly changes
 * @param onUseTrendChange Callback when useTrend changes
 * @param onUseSuperBolusChange Callback when useSuperBolus changes
 * @param onUseTempTargetChange Callback when useTempTarget changes
 * @param onUseAlarmChange Callback when useAlarm changes
 * @param onPercentageChange Callback when percentage changes
 * @param onDevicePhoneChange Callback when device phone changes
 * @param onDeviceWatchChange Callback when device watch changes
 * @param onUseEcarbsChange Callback when useEcarbs changes
 * @param onTimeChange Callback when time changes
 * @param onDurationChange Callback when duration changes
 * @param onCarbs2Change Callback when carbs2 changes
 * @param modifier Modifier for the editor
 */
@Composable
fun QuickWizardEditor(
    mode: QuickWizardMode,
    buttonText: String,
    insulin: Double,
    carbs: Int,
    carbTime: Int,
    validFrom: Int,
    validTo: Int,
    useBG: Boolean,
    useCOB: Boolean,
    useIOB: Boolean,
    usePositiveIOBOnly: Boolean,
    useTrend: TrendOption,
    useSuperBolus: Boolean,
    useTempTarget: Boolean,
    useAlarm: Boolean,
    percentage: Int,
    devicePhone: Boolean,
    deviceWatch: Boolean,
    useEcarbs: Boolean,
    time: Int,
    duration: Int,
    carbs2: Int,
    showSuperBolusOption: Boolean,
    showWearOptions: Boolean,
    maxCarbs: Double,
    maxInsulin: Double,
    rh: ResourceHelper,
    onModeChange: (QuickWizardMode) -> Unit,
    onButtonTextChange: (String) -> Unit,
    onInsulinChange: (Double) -> Unit,
    onCarbsChange: (Int) -> Unit,
    onCarbTimeChange: (Int) -> Unit,
    onValidFromChange: (Int) -> Unit,
    onValidToChange: (Int) -> Unit,
    onUseBGChange: (Boolean) -> Unit,
    onUseCOBChange: (Boolean) -> Unit,
    onUseIOBChange: (Boolean) -> Unit,
    onUsePositiveIOBOnlyChange: (Boolean) -> Unit,
    onUseTrendChange: (TrendOption) -> Unit,
    onUseSuperBolusChange: (Boolean) -> Unit,
    onUseTempTargetChange: (Boolean) -> Unit,
    onUseAlarmChange: (Boolean) -> Unit,
    onPercentageChange: (Int) -> Unit,
    onDevicePhoneChange: (Boolean) -> Unit,
    onDeviceWatchChange: (Boolean) -> Unit,
    onUseEcarbsChange: (Boolean) -> Unit,
    onTimeChange: (Int) -> Unit,
    onDurationChange: (Int) -> Unit,
    onCarbs2Change: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LocalDateUtil.current
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Button Text
        OutlinedTextField(
            value = buttonText,
            onValueChange = onButtonTextChange,
            label = { Text(stringResource(R.string.overview_edit_quickwizard_button_text)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Mode selector
        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            QuickWizardMode.entries.forEach { m ->
                SegmentedButton(
                    selected = mode == m,
                    onClick = { onModeChange(m) },
                    shape = SegmentedButtonDefaults.itemShape(index = m.ordinal, count = QuickWizardMode.entries.size),
                    icon = {
                        SegmentedButtonDefaults.Icon(active = mode == m) {
                            Icon(
                                imageVector = when (m) {
                                    QuickWizardMode.WIZARD  -> IcQuickwizard
                                    QuickWizardMode.INSULIN -> IcBolus
                                    QuickWizardMode.CARBS   -> IcCarbs
                                },
                                contentDescription = null,
                                modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                            )
                        }
                    }
                ) {
                    Text(
                        text = when (m) {
                            QuickWizardMode.WIZARD  -> stringResource(R.string.quick_wizard_mode_wizard)
                            QuickWizardMode.INSULIN -> stringResource(R.string.quick_wizard_mode_insulin)
                            QuickWizardMode.CARBS   -> stringResource(R.string.quick_wizard_mode_carbs)
                        }
                    )
                }
            }
        }

        // Insulin (INSULIN mode only)
        if (mode == QuickWizardMode.INSULIN) {
            NumberInputRow(
                labelResId = CoreR.string.overview_insulin_label,
                value = insulin,
                onValueChange = onInsulinChange,
                valueRange = 0.0..maxInsulin,
                step = 0.05,
                decimalPlaces = 2,
                unitLabel = stringResource(CoreR.string.insulin_unit_shortname),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Carbs (WIZARD and CARBS modes)
        if (mode != QuickWizardMode.INSULIN) {
            NumberInputRow(
                labelResId = CoreR.string.carbs,
                value = carbs.toDouble(),
                onValueChange = { onCarbsChange(it.toInt()) },
                valueRange = 0.0..maxCarbs,
                step = 1.0,
                unitLabelResId = KeysR.string.units_grams,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Carb Time (WIZARD mode only)
        if (mode == QuickWizardMode.WIZARD) {
            NumberInputRow(
                labelResId = R.string.carb_time,
                value = carbTime.toDouble(),
                onValueChange = { onCarbTimeChange(it.toInt()) },
                valueRange = -60.0..60.0,
                step = 5.0,
                unitLabelResId = KeysR.string.units_min,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Valid Time Range
        TimeRangePicker(
            label = stringResource(R.string.valid_from_to),
            startSeconds = validFrom,
            endSeconds = validTo,
            onStartChange = onValidFromChange,
            onEndChange = onValidToChange,
            modifier = Modifier.fillMaxWidth()
        )

        // Calculator Options (WIZARD mode only)
        if (mode == QuickWizardMode.WIZARD) {
        HorizontalDivider()

        // Calculator Options Section
        Text(
            text = stringResource(R.string.calculator_options),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // Use BG
        SwitchRow(
            label = stringResource(R.string.use_bg),
            checked = useBG,
            onCheckedChange = onUseBGChange
        )

        // Use COB
        SwitchRow(
            label = stringResource(R.string.use_cob),
            checked = useCOB,
            onCheckedChange = onUseCOBChange
        )

        // Use IOB
        SwitchRow(
            label = stringResource(R.string.use_iob),
            checked = useIOB,
            onCheckedChange = onUseIOBChange
        )

        // Use Positive IOB Only (only visible when IOB enabled)
        if (useIOB) {
            SwitchRow(
                label = stringResource(R.string.overview_edit_quickwizard_use_positive_iob_only),
                checked = usePositiveIOBOnly,
                onCheckedChange = onUsePositiveIOBOnlyChange,
                modifier = Modifier.padding(start = 16.dp)
            )
        }

        // Use Trend
        Column(modifier = Modifier.fillMaxWidth()) {
            SwitchRow(
                label = stringResource(R.string.use_trend),
                checked = useTrend != TrendOption.NO,
                onCheckedChange = { enabled ->
                    onUseTrendChange(if (enabled) TrendOption.YES else TrendOption.NO)
                }
            )
            // Trend options (only visible when trend enabled)
            if (useTrend != TrendOption.NO) {
                Column(modifier = Modifier.padding(start = 16.dp, top = 8.dp)) {
                    TrendRadioButton(
                        label = stringResource(R.string.trend_all),
                        selected = useTrend == TrendOption.YES,
                        onClick = { onUseTrendChange(TrendOption.YES) }
                    )
                    TrendRadioButton(
                        label = stringResource(R.string.trend_positive_only),
                        selected = useTrend == TrendOption.POSITIVE_ONLY,
                        onClick = { onUseTrendChange(TrendOption.POSITIVE_ONLY) }
                    )
                    TrendRadioButton(
                        label = stringResource(R.string.trend_negative_only),
                        selected = useTrend == TrendOption.NEGATIVE_ONLY,
                        onClick = { onUseTrendChange(TrendOption.NEGATIVE_ONLY) }
                    )
                }
            }
        }

        // Use Super Bolus (only if enabled in preferences)
        if (showSuperBolusOption) {
            SwitchRow(
                label = stringResource(R.string.overview_edit_quickwizard_superbolus),
                checked = useSuperBolus,
                onCheckedChange = onUseSuperBolusChange
            )
        }

        // Use Temp Target
        SwitchRow(
            label = stringResource(R.string.use_temp_target),
            checked = useTempTarget,
            onCheckedChange = onUseTempTargetChange
        )

        // Alarm
        SwitchRow(
            label = stringResource(R.string.use_alarm),
            checked = useAlarm,
            onCheckedChange = onUseAlarmChange,
            icon = Icons.Default.Alarm
        )

        HorizontalDivider()

        // Percentage
        NumberInputRow(
            labelResId = KeysR.string.pref_title_bolus_percentage,
            value = percentage.toDouble(),
            onValueChange = { onPercentageChange(it.toInt()) },
            valueRange = 10.0..200.0,
            step = 5.0,
            unitLabelResId = KeysR.string.units_percent,
            modifier = Modifier.fillMaxWidth()
        )

        } // end WIZARD-only section

        // Device Selection (only if wear control enabled)
        if (showWearOptions) {
            HorizontalDivider()
            Text(
                text = stringResource(R.string.device_selection),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            SwitchRow(
                label = stringResource(R.string.show_on_phone),
                checked = devicePhone,
                onCheckedChange = onDevicePhoneChange,
                icon = Icons.Default.PhoneAndroid
            )

            SwitchRow(
                label = stringResource(R.string.show_on_watch),
                checked = deviceWatch,
                onCheckedChange = onDeviceWatchChange,
                icon = Icons.Default.Watch
            )
        }

        // Extended Carbs Section (WIZARD and CARBS modes)
        if (mode != QuickWizardMode.INSULIN) {
        HorizontalDivider()
        SwitchRow(
            label = stringResource(R.string.additional_ecarbs),
            checked = useEcarbs,
            onCheckedChange = onUseEcarbsChange
        )

        if (useEcarbs) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Time offset
                NumberInputRow(
                    labelResId = R.string.time_offset,
                    value = time.toDouble(),
                    onValueChange = { onTimeChange(it.toInt()) },
                    valueRange = (-7 * 24 * 60).toDouble()..(12 * 60).toDouble(),
                    step = 5.0,
                    unitLabelResId = KeysR.string.units_min,
                    modifier = Modifier.fillMaxWidth()
                )

                // Duration
                NumberInputRow(
                    labelResId = CoreR.string.duration,
                    value = duration.toDouble(),
                    onValueChange = { onDurationChange(it.toInt()) },
                    valueRange = 0.0..10.0,
                    step = 1.0,
                    unitLabelResId = KeysR.string.units_hours,
                    modifier = Modifier.fillMaxWidth()
                )

                // Additional carbs
                NumberInputRow(
                    labelResId = R.string.ecarbs_additional,
                    value = carbs2.toDouble(),
                    onValueChange = { onCarbs2Change(it.toInt()) },
                    valueRange = 0.0..maxCarbs,
                    step = 1.0,
                    unitLabelResId = KeysR.string.units_grams,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        } // end non-INSULIN section
    }
}

/**
 * Reusable switch row component
 */
@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * Radio button for trend selection
 */
@Composable
private fun TrendRadioButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.RadioButton(
            selected = selected,
            onClick = onClick
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
