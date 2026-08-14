package app.aaps.core.interfaces.rx

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen

/**
 * Collects a long-lived [Flow] resiliently in [scope], logging under [tag].
 *
 * Kotlin Flow makes exceptions terminal: a throwable raised while processing an emission cancels the
 * collecting coroutine **permanently**, after which every later emission is silently dropped. For a
 * hot, side-effecting collector (DB-change reactions, event handlers) that means a feature quietly
 * stops working until the process is restarted — with no crash and no log.
 *
 * This wrapper makes such a collector self-healing:
 *  - per-emission failures are caught and logged, so a single bad item never cancels the collector;
 *  - anything that still escapes (e.g. a failure in the upstream/source flow) restarts the collection
 *    after [restartDelayMs] instead of killing it for good.
 *
 * [CancellationException] is always propagated, so structured-concurrency cancellation (scope
 * shutdown) is honored and never swallowed or retried.
 *
 * NOTE: this only addresses *exceptions*. Collection is sequential, so a [block] that never returns
 * (e.g. an awaited callback that is lost) still blocks all subsequent emissions without throwing —
 * bound such calls with a timeout (`withTimeoutOrNull`) at the call site.
 */
fun <T> Flow<T>.collectResilient(
    scope: CoroutineScope,
    aapsLogger: AAPSLogger,
    tag: LTag,
    restartDelayMs: Long = 1000L,
    block: suspend (T) -> Unit
): Job =
    onEach { item ->
        try {
            block(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            aapsLogger.error(tag, "Resilient collector: item processing failed, continuing", e)
        }
    }
        .retryWhen { cause, attempt ->
            if (cause is CancellationException) {
                false // honor structured-concurrency cancellation (e.g. scope shutdown)
            } else {
                aapsLogger.error(tag, "Resilient collector failed (attempt ${attempt + 1}), restarting", cause)
                delay(restartDelayMs)
                true
            }
        }
        .launchIn(scope)
