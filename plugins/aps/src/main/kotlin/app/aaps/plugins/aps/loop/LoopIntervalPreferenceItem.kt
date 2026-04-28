package app.aaps.plugins.aps.loop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.unitLabelResId
import app.aaps.core.keys.valueResId
import app.aaps.core.ui.compose.preference.CustomPreferenceItem
import app.aaps.core.ui.compose.preference.LocalPreferenceTheme
import app.aaps.core.ui.compose.preference.PreferenceSliderWithButtons
import app.aaps.core.ui.compose.preference.rememberPreferenceIntState
import app.aaps.plugins.aps.R
import java.text.DecimalFormat

/**
 * Loop frequency-limit slider with two live readouts beneath it:
 *   - selected-mode estimate (e.g. "~480 loops/day" or "every reading")
 *   - actual loops in the last 24h, escalating to a warning when usage is high or
 *     the user picks a near-1-minute floor.
 *
 * Pure-key [IntPreferenceKey] rendering can't show those without coupling the
 * framework to APS data, hence the [CustomPreferenceItem] subclass.
 */
class LoopIntervalPreferenceItem(
    private val persistenceLayer: PersistenceLayer
) : CustomPreferenceItem() {

    @Composable
    override fun Content() {
        val intKey = IntKey.LoopMinBgRecalcInterval
        val state = rememberPreferenceIntState(intKey)
        val value = state.value
        val theme = LocalPreferenceTheme.current

        var dailyCount by remember { mutableStateOf<Int?>(null) }
        // Re-query on each selection change so the warning state stays in sync after the
        // user nudges the slider; the count itself doesn't depend on `value`, but recompute
        // is cheap and keeps the estimate/warning evaluation paired.
        LaunchedEffect(value) {
            val now = System.currentTimeMillis()
            dailyCount = persistenceLayer.getApsResults(now - 24 * 60 * 60 * 1000L, now).size
        }

        val unitLabelResId = intKey.unitType.unitLabelResId() ?: 0
        val valueFormatResId = intKey.unitType.valueResId()
        val title = stringResource(intKey.titleResId)
        val summary = intKey.summaryResId?.takeIf { it != 0 }?.let { stringResource(it) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(theme.listItemPadding)
        ) {
            Text(text = title, style = theme.titleTextStyle, color = theme.titleColor)
            if (summary != null) {
                Text(text = summary, style = theme.summaryTextStyle, color = theme.summaryColor)
            }

            PreferenceSliderWithButtons(
                value = value.toDouble(),
                onValueChange = { newValue -> state.value = newValue.toInt() },
                valueRange = intKey.min.toDouble()..intKey.max.toDouble(),
                step = 1.0,
                showValue = true,
                valueFormatResId = valueFormatResId,
                formatAsInt = true,
                valueFormat = DecimalFormat("0"),
                unitLabelResId = unitLabelResId,
                dialogLabel = title,
                dialogSummary = summary
            )

            val estimate = if (value == 0) stringResource(R.string.loop_recalc_every_sensor_reading)
            else stringResource(R.string.loop_recalc_loops_per_day, 1440 / value)
            Text(
                text = estimate,
                style = theme.summaryTextStyle,
                fontWeight = FontWeight.SemiBold,
                color = theme.titleColor,
                modifier = Modifier.padding(top = 4.dp)
            )

            dailyCount?.let { count ->
                val (text, isWarning) = when {
                    count > 500    -> stringResource(R.string.loop_recalc_daily_count_warning, count) to true
                    value in 1..2  -> stringResource(R.string.loop_recalc_high_rate_warning) to true
                    else           -> stringResource(R.string.loop_recalc_daily_count, count) to false
                }
                Text(
                    text = text,
                    style = theme.summaryTextStyle,
                    color = if (isWarning) MaterialTheme.colorScheme.error else theme.summaryColor,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
