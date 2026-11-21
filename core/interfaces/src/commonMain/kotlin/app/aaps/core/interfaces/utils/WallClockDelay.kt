package app.aaps.core.interfaces.utils

import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Waits until this clock reaches [targetMs], in epoch milliseconds.
 *
 * Not one long `delay`. A coroutine delay counts only the time the device is awake, and a locked iPhone
 * sleeps. So `delay(30 minutes)` started before the phone slept for an hour ends about an hour late,
 * even when the app is running again by then: a running mode with insulin switched off would stay on
 * that long past its end. This waits in steps of at most [step] and reads the clock after each one, so
 * once the app runs, the wait ends no more than one [step] after the real time.
 *
 * Use it for anything that has to happen at a time of day, not after an amount of work.
 */
suspend fun Clock.delayUntil(targetMs: Long, step: Duration = 30.seconds) {
    val stepMs = step.inWholeMilliseconds
    while (true) {
        val leftMs = targetMs - now().toEpochMilliseconds()
        if (leftMs <= 0) return
        delay(minOf(leftMs, stepMs))
    }
}
