package app.aaps.core.objects.extensions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * Periodic `Unit` emitter — emits once immediately, then again every [periodMs]. The "emit
 * first" shape means a fresh subscriber gets an evaluation tick straight away instead of
 * waiting a full period.
 *
 * Use for time-dependent derivations (staleness checks, countdown re-renders) where the
 * upstream state inputs alone wouldn't trigger re-evaluation.
 */
fun tickerFlow(periodMs: Long): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(periodMs)
    }
}

/**
 * "Is this timestamp recent enough right now?" — combines a `Long`-typed timestamp `StateFlow`
 * (millis epoch) with an internal [tickerFlow] so the result re-evaluates with wall-clock
 * progression even when the timestamp itself doesn't change.
 *
 * Returns a `StateFlow<Boolean>` that's true when `now() - timestamp.value < thresholdMs`.
 *
 * **Pre-first-value (`timestamp == 0L`):** forwarded to [pristine]. Most callers want `true`
 * here — don't gate something off before the producer has had a chance to emit a heartbeat.
 * Callers that prefer fail-closed semantics (refuse to act before any signal arrives) can
 * pass `pristine = false`.
 *
 * Example:
 * ```
 * val masterAlive = nsClient.lastDevicestatusReceivedAt.freshness(
 *     thresholdMs = 9 * 60_000L,
 *     scope = viewModelScope
 * )
 * ```
 */
fun StateFlow<Long>.freshness(
    thresholdMs: Long,
    scope: CoroutineScope,
    tickMs: Long = 60_000L,
    pristine: Boolean = true,
    now: () -> Long = { System.currentTimeMillis() }
): StateFlow<Boolean> =
    combine(this, tickerFlow(tickMs)) { ts, _ ->
        if (ts == 0L) pristine
        else now() - ts < thresholdMs
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), pristine)
