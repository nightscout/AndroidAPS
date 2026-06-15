package app.aaps.helpers

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

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
        type: Class<T>,
        what: String = type.simpleName,
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
}
