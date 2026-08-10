/*
 * Adaptive Unit Double Preference for Jetpack Compose
 */

package app.aaps.core.ui.compose.preference

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.compose.stringResourceOrNull
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import app.aaps.core.ui.R as UiR

/**
 * Composable unit double preference for use inside card sections.
 * Handles glucose unit conversion (mg/dL <-> mmol/L).
 *
 * @param title Optional title override. If null, uses unitKey.title
 * @param visibilityContext Optional context for evaluating runtime visibility/enabled conditions
 */
@Composable
fun AdaptiveUnitDoublePreferenceItem(
    unitKey: UnitDoublePreferenceKey,
    title: TextRef? = null,
    visibilityContext: VisibilityContext? = null
) {
    val preferences = LocalPreferences.current
    val profileUtil = LocalProfileUtil.current
    val effectiveTitle = title ?: unitKey.title

    val visibility = calculatePreferenceVisibility(
        preferenceKey = unitKey,
        visibilityContext = visibilityContext
    )

    if (!visibility.visible || (preferences.simpleMode && unitKey.defaultedBySM)) return

    val state = rememberUnitDoublePreferenceState(unitKey)
    val theme = LocalPreferenceTheme.current

    // Convert min/max values from mg/dL to current units using ProfileUtil
    val minDisplay = profileUtil.fromMgdlToUnits(unitKey.minMgdl.toDouble())
    val maxDisplay = profileUtil.fromMgdlToUnits(unitKey.maxMgdl.toDouble())

    // Detect if using mg/dL by checking if conversion preserved the value
    val isMgdl = abs(minDisplay - unitKey.minMgdl.toDouble()) < 0.01

    // Adaptive step: 1.0 for mg/dL, 0.1 for mmol/L
    val step = if (isMgdl) 1.0 else 0.1
    val decimalPlaces = if (isMgdl) 0 else 1
    val valueFormat = if (isMgdl) NumberFormat.INTEGER else NumberFormat.DECIMAL_1

    // Get unit label from resources - short form for slider
    val unitLabel = TextRef.AndroidRes(if (isMgdl) UiR.string.mgdl else UiR.string.mmol)

    // Get summary if available
    val summary = stringResourceOrNull(unitKey.summary)

    // Parse current display value to Double
    val currentValue = state.displayValue.toDoubleOrNull() ?: minDisplay

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(theme.padding)
    ) {
        TextWithSyncBadge(
            text = stringResource(effectiveTitle),
            key = unitKey,
            style = theme.titleTextStyle,
            // Mirror Preference's disabled styling (the switch row greys the same way) since this
            // slider branch builds its own row instead of going through Preference.
            color = theme.titleColor.let { if (visibility.enabled) it else it.copy(alpha = theme.disabledOpacity) }
        )
        if (summary != null) {
            Text(
                text = summary,
                style = theme.summaryTextStyle,
                color = theme.summaryColor.let { if (visibility.enabled) it else it.copy(alpha = theme.disabledOpacity) }
            )
        }
        PreferenceSliderWithButtons(
            value = currentValue,
            onValueChange = { newValue ->
                if (visibility.enabled) {
                    // Format with appropriate precision and update state
                    val formatted = BigDecimal(newValue).setScale(decimalPlaces, RoundingMode.HALF_UP).toPlainString()
                    state.updateDisplayValue(formatted)
                }
            },
            valueRange = minDisplay..maxDisplay,
            step = step,
            showValue = true,
            valueFormat = valueFormat,
            unitLabel = unitLabel,
            dialogLabel = stringResource(effectiveTitle),
            dialogSummary = summary,
            enabled = visibility.enabled
        )
    }
}
