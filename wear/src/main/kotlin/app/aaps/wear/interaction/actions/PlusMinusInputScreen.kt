package app.aaps.wear.interaction.actions

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.coroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.AnchorType
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.curvedText
import app.aaps.wear.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import kotlin.math.round
import kotlin.math.roundToInt

// ─── Shared colors for wear action activities ─────────────────────────────────
internal val InsulinBlue         = Color(0xFF67DFE8)
internal val CarbsOrange         = Color(0xFFFB8C00)
internal val ConfirmGreen        = Color(0xFF66BB6A)
internal val WearInsulinPositive = Color(0xFF66BB6A)
internal val WearInsulinNegative = Color(0xFFEF5350)
internal val WearSecondaryText   = Color(0xFFB0BEC5)
internal val WearDivider         = Color(0xFF37474F)
internal val WearSummaryCardBg   = Color(0xFF0D47A1)
internal val WearCalcCardBg      = Color(0xFF263238)
internal val TempTargetYellow       = Color(0xFFF4D700)
internal val LoopClosedColor        = Color(0xFF00C03E)
internal val LoopOpenColor          = Color(0xFF4983D7)
internal val LoopLgsColor           = Color(0xFF800080)
internal val LoopSuspendedColor     = Color(0xFFFFFF13)
internal val LoopDisabledColor      = Color(0xFFFF1313)
internal val LoopDisconnectedColor  = Color(0xFF939393)
internal val LoopSuperbolusColor    = Color(0xFFFFAE01)
internal val LoopUnknownColor       = Color(0xFF9E9E9E)

private val ButtonBg = Color.White.copy(alpha = 0.15f)

// Button offsets derived from constraint circle: 70dp radius at 35° from vertical
// x = 70 * sin(35°) ≈ 40dp,  y = 70 * cos(35°) ≈ 57dp
private val BtnH = 40.dp
private val BtnV = 57.dp

/**
 * Circular +/- input screen matching the action_editplusmin_multi.xml layout.
 *
 * Button positions around the center value:
 *   Bottom-left  (-40dp, +57dp): decrement  (stepValues[0])
 *   Bottom-right (+40dp, +57dp): increment  (stepValues[0], icon)
 *   Top-right    (+40dp, -57dp): increment  (stepValues[1], labeled)
 *   Top-left     (-40dp, -57dp): increment  (stepValues[2], labeled)
 *
 * @param isActive pass true when this page is currently visible (needed for rotary focus)
 */
@Composable
internal fun PlusMinusInputScreen(
    value: Double,
    onValueChange: (Double) -> Unit,
    min: Double,
    max: Double,
    stepValues: List<Double>,
    format: DecimalFormat,
    label: String,
    displayText: String? = null,
    allowZero: Boolean = false,
    isActive: Boolean = true,
    symmetricLargeSteps: Boolean = false,
    simpleMode: Boolean = false,
    enabled: Boolean = true,
    valueColor: Color = Color.White,
    stepLabels: List<String>? = null,
    title: String? = null,
) {
    val haptic = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }
    var accumulatedScroll by remember { mutableFloatStateOf(0f) }

    // Internal mutable state — updated immediately on every step so that
    // the captured step() function always sees the current value regardless
    // of whether Compose has recomposed yet.
    val currentValue = remember { mutableStateOf(value) }
    SideEffect { currentValue.value = value }

    // Rounding factor derived from the fine step to prevent floating point
    // accumulation (e.g. 0.1 + 0.1 + 0.1 - 0.1 - 0.1 - 0.1 ≠ 0.0 without rounding).
    val roundingFactor = remember(stepValues[0]) {
        if (stepValues[0] < 1.0) (1.0 / stepValues[0]).roundToInt().toDouble() else 1.0
    }

    LaunchedEffect(isActive) {
        if (isActive) focusRequester.requestFocus()
    }

    fun step(delta: Double) {
        val v = currentValue.value
        val newValue = (round((v + delta) * roundingFactor) / roundingFactor).coerceIn(min, max)
        if (newValue != v) {
            currentValue.value = newValue   // update immediately for next step
            onValueChange(newValue)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val displayValue = displayText ?: if (currentValue.value == 0.0 && !allowZero) "" else format.format(currentValue.value)
    val valueFontSize = if (displayText != null) 32.sp else 40.sp
    val labelFontSize = when {
        label.length <= 16 -> 20.sp
        label.length <= 22 -> 16.sp
        label.length <= 28 -> 13.sp
        else               -> 11.sp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onRotaryScrollEvent { event ->
                accumulatedScroll += event.verticalScrollPixels
                val threshold = 16f
                while (accumulatedScroll > threshold) {
                    accumulatedScroll -= threshold
                    step(-stepValues[0])
                }
                while (accumulatedScroll < -threshold) {
                    accumulatedScroll += threshold
                    step(stepValues[0])
                }
                true
            },
        contentAlignment = Alignment.Center
    ) {
        if (title != null) {
            val titleFontSize = when {
                title.length <= 15 -> 12.sp
                title.length <= 19 -> 10.sp
                else               -> 9.sp
            }
            CurvedLayout(anchor = 270f, anchorType = AnchorType.Center) {
                curvedText(
                    text = title,
                    color = WearSecondaryText,
                    fontSize = titleFontSize,
                )
            }
        }
        if (simpleMode) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StepButton(step = stepValues[0], isIncrement = false, onStep = ::step, enabled = enabled)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = displayValue,
                        color = valueColor,
                        fontSize = valueFontSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = label,
                        color = WearSecondaryText,
                        fontSize = labelFontSize,
                        textAlign = TextAlign.Center,
                    )
                }
                StepButton(step = stepValues[0], isIncrement = true, onStep = ::step, enabled = enabled)
            }
        } else {
            // Center: value + unit label
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = displayValue,
                    color = valueColor,
                    fontSize = valueFontSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = label,
                    color = WearSecondaryText,
                    fontSize = labelFontSize,
                    textAlign = TextAlign.Center,
                )
            }

            // Bottom-left: decrement (fine step)
            StepButton(
                modifier = Modifier.align(Alignment.Center).offset(-BtnH, BtnV),
                step = stepValues[0],
                isIncrement = false,
                onStep = ::step,
                enabled = enabled,
            )

            // Bottom-right: increment (fine step, icon)
            StepButton(
                modifier = Modifier.align(Alignment.Center).offset(BtnH, BtnV),
                step = stepValues[0],
                isIncrement = true,
                onStep = ::step,
                enabled = enabled,
            )

            // Top-right: medium/large increment (labeled)
            StepButton(
                modifier = Modifier.align(Alignment.Center).offset(BtnH, -BtnV),
                step = stepValues[1],
                isIncrement = true,
                onStep = ::step,
                useTextLabel = true,
                enabled = enabled,
                labelOverride = stepLabels?.getOrNull(0),
            )

            // Top-left: large increment or symmetric decrement (labeled)
            StepButton(
                modifier = Modifier.align(Alignment.Center).offset(-BtnH, -BtnV),
                step = if (symmetricLargeSteps) stepValues[1] else stepValues[2],
                isIncrement = !symmetricLargeSteps,
                onStep = ::step,
                useTextLabel = true,
                enabled = enabled,
                labelOverride = stepLabels?.getOrNull(1),
            )
        }
    }
}

@Composable
private fun StepButton(
    modifier: Modifier = Modifier,
    step: Double,
    isIncrement: Boolean,
    onStep: (Double) -> Unit,
    useTextLabel: Boolean = false,
    enabled: Boolean = true,
    labelOverride: String? = null,
) {
    val delta = if (isIncrement) step else -step

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(ButtonBg)
            .pointerInput(delta, enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        var stepped = false
                        coroutineScope {
                            val job = launch {
                                delay(100)          // allow pager to claim swipe gestures first
                                stepped = true
                                onStep(delta)
                                var repeatDelay = 300L
                                while (true) {
                                    delay(repeatDelay)
                                    onStep(delta)
                                    repeatDelay = maxOf(40L, (repeatDelay * 0.75).toLong())
                                }
                            }
                            val released = tryAwaitRelease()  // false if pager cancelled the gesture
                            job.cancel()
                            if (!stepped && released) onStep(delta)  // quick tap under 100ms threshold
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (useTextLabel) {
            val label = labelOverride ?: remember(step, isIncrement) {
                val fmt = DecimalFormat("#.#")
                val prefix = if (isIncrement) "+" else "-"
                "$prefix${fmt.format(step).replaceFirst("^0+(?!$)".toRegex(), "")}"
            }
            Text(text = label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        } else {
            Icon(
                painter = painterResource(if (isIncrement) R.drawable.ic_action_add else R.drawable.ic_action_minus),
                contentDescription = if (isIncrement) "+" else "-",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
internal fun CurvedTitle(title: String) {
    val fontSize = when {
        title.length <= 15 -> 12.sp
        title.length <= 19 -> 10.sp
        else               -> 9.sp
    }
    CurvedLayout(anchor = 270f, anchorType = AnchorType.Center) {
        curvedText(text = title, color = WearSecondaryText, fontSize = fontSize)
    }
}

@Composable
internal fun formatDurationMinutes(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val mins = totalMinutes % 60
    return when {
        hours == 0 -> stringResource(R.string.action_minutes_format, totalMinutes)
        mins == 0  -> stringResource(R.string.action_duration_hours_format, hours)
        else       -> stringResource(R.string.action_duration_hours_minutes_format, hours, mins)
    }
}
