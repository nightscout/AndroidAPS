package app.aaps.spike.cmp

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Copied from `:core:ui`'s SliderWithButtons.kt, with ONE change, and that change is a finding.
 *
 * The original reads:
 * ```
 * val factor = Math.pow(10.0, decimals.toDouble())
 * return Math.round(scaled * factor) / factor
 * ```
 * `Math` is `java.lang.Math` - JVM only. `java.lang` needs no import statement, so no amount of
 * grepping imports finds it; the file looks clean and is not. That is the same mistake shape the
 * feasibility note already records twice, and it means the count of `:core:ui` files that can move
 * is optimistic wherever it was derived from imports alone.
 *
 * `kotlin.math` is a drop-in replacement and works on every target.
 */
internal fun roundToStep(value: Double, step: Double): Double {
    val scaled = (value / step).roundToInt() * step
    // Fix floating point precision errors (e.g., 6.1000000000005 -> 6.1)
    val decimals = step.toString().substringAfter('.', "").length
    val factor = 10.0.pow(decimals.toDouble())
    return (scaled * factor).roundToLong() / factor
}

/**
 * Copied verbatim from `:core:ui`. Kept because it exercises pointer input, haptics and a coroutine
 * loop - three things a trivial spike screen would not touch.
 */
@Composable
fun RepeatingIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    initialDelayMs: Long = 500L,
    maxDelayMs: Long = 200L,
    minDelayMs: Long = 50L,
    accelerationFactor: Float = 0.8f,
    content: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isPressed, enabled) {
        if (isPressed && enabled) {
            delay(initialDelayMs)
            var currentDelay = maxDelayMs.toFloat()
            while (isPressed && enabled) {
                currentOnClick()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                delay(currentDelay.toLong())
                currentDelay = (currentDelay * accelerationFactor).coerceAtLeast(minDelayMs.toFloat())
            }
        }
    }

    FilledTonalIconButton(
        onClick = {
            onClick()
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        },
        enabled = enabled,
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                isPressed = true
                waitForUpOrCancellation()
                isPressed = false
            }
        }
    ) {
        content()
    }
}
