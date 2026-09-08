package app.aaps.helpers

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import dev.zacsweers.metro.Inject
import kotlin.reflect.KClass

/**
 * Suspend-first waiting helpers for integration tests.
 *
 * Replaces two fragile patterns that were copy-pasted across the integration tests:
 *  - hand-rolled `CoroutineScope(Dispatchers.IO).async { withTimeout(40_000) { observeChanges(..).first() } }`
 *    blocks (verbose, and on timeout fail with an opaque `TimeoutCancellationException: Timed out
 *    waiting for 40000 ms` that says nothing about *what* was awaited), and
 *  - blind `delay(2000)` / `Thread.sleep(2000)` settles after a calculation (always pay the full 2s,
 *    and `Thread.sleep` blocks the dispatcher — counter to the suspend-first direction).
 */
class IntegrationWaits @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    private val iobCobCalculator: IobCobCalculator,
    private val aapsLogger: AAPSLogger
) {

    /**
     * Run [action], then suspend until the first database change of [type] is observed, and return it.
     *
     * The observer is started *before* [action] runs, so an emission triggered by [action] can never
     * be missed. The observe + timeout run on [Dispatchers.IO] (real time) on purpose: these tests
     * execute inside `runTest`, whose virtual clock would otherwise make the timeout fire instantly.
     * On timeout this fails with a message naming [what] instead of an opaque coroutine timeout.
     */
    suspend fun <T : Any> awaitDbChange(
        type: KClass<T>,
        what: String = type.simpleName ?: "?",
        timeoutMs: Long = 40_000,
        action: suspend () -> Unit
    ): List<T> = coroutineScope {
        val deferred = async(Dispatchers.IO) {
            withTimeoutOrNull(timeoutMs) { persistenceLayer.observeChanges(type).first() }
        }
        action()
        deferred.await() ?: error("Timed out after ${timeoutMs}ms waiting for database change of $what")
    }

    /**
     * Suspend until the autosens calculation thread is idle.
     *
     * Deterministic replacement for a fixed `delay(2000)` / `Thread.sleep(2000)` settle: returns as
     * soon as the calculation actually finished (the underlying join is bounded at 5s), so the
     * non-waiting COB-timeline reads that follow observe a fully computed state. The blocking join
     * runs off the test dispatcher on [Dispatchers.IO].
     */
    suspend fun awaitCalculationFinished(reason: String = "test") {
        withContext(Dispatchers.IO) {
            iobCobCalculator.getLastAutosensDataWithWaitForCalculationFinish(reason)
        }
        aapsLogger.debug(LTag.AUTOSENS, "IntegrationWaits: calculation settled ($reason)")
    }

    /**
     * Suspend until [condition] holds. Throws (naming [what]) if it has not held within [timeoutMs].
     *
     * Suspend-first replacement for `RxHelper.waitUntil(...)` followed by `assertThat(it).isTrue()`.
     * Three differences that matter under CI load:
     *  - it *suspends* between polls instead of `Thread.sleep`-ing the very thread `runTest` drives, so
     *    the test-coroutine machinery keeps running while the condition is pending;
     *  - the poll loop runs on [Dispatchers.IO] (real time) on purpose — inside `runTest` a `delay` on
     *    the virtual clock advances instantly, so the loop would spin without any real time passing;
     *  - on timeout it fails with a message naming [what], instead of returning `false` into an
     *    `isTrue()` assertion whose entire failure message is "expected to be true".
     */
    suspend fun awaitCondition(
        what: String,
        timeoutMs: Long = 60_000,
        pollMs: Long = 100,
        condition: suspend () -> Boolean
    ) {
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(timeoutMs) {
                while (!condition()) delay(pollMs)
            } ?: error("Timed out after ${timeoutMs}ms waiting for $what")
        }
        aapsLogger.debug(LTag.CORE, "IntegrationWaits: condition met ($what)")
    }

    /**
     * Suspend until [isBusy] has reported `false` continuously for [quietMs].
     *
     * Deterministic replacement for a blind "sleep a bit and hope the previous test's coroutines have
     * landed" settle: a single sample of an empty queue proves nothing, because the emission we are
     * racing may not have arrived yet — only *sustained* quiet does. Returns as soon as the system is
     * actually idle, so the common case is far quicker than the fixed sleep it replaces.
     *
     * Best-effort by design: a still-busy system logs a warning rather than failing, matching the
     * semantics of the fixed sleep (which also just carried on) — callers follow this with an explicit
     * reset such as `commandQueue.clear()`.
     */
    suspend fun awaitQuiet(
        what: String,
        quietMs: Long = 300,
        timeoutMs: Long = 10_000,
        pollMs: Long = 50,
        isBusy: suspend () -> Boolean
    ) {
        val settled = withContext(Dispatchers.IO) {
            withTimeoutOrNull(timeoutMs) {
                var quietFor = 0L
                while (quietFor < quietMs) {
                    delay(pollMs)
                    if (isBusy()) quietFor = 0L else quietFor += pollMs
                }
                true
            }
        }
        if (settled == null) aapsLogger.warn(LTag.CORE, "IntegrationWaits: $what still busy after ${timeoutMs}ms, continuing")
        else aapsLogger.debug(LTag.CORE, "IntegrationWaits: $what quiet for ${quietMs}ms")
    }
}
