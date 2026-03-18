package app.aaps.core.interfaces.pump

import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow

/**
 * A [StateFlow] that lazily transforms values from an upstream [StateFlow].
 *
 * Unlike `stateIn()` or `onEach/launchIn`, this does NOT require a [CoroutineScope].
 * The [transform] runs synchronously on every [value] access and during collection,
 * so keep it cheap (e.g. wrapping a Double in PumpInsulin).
 */
class MappedStateFlow<T, R>(
    private val upstream: StateFlow<T>,
    private val transform: (T) -> R
) : StateFlow<R> {

    override val value: R get() = transform(upstream.value)

    override val replayCache: List<R> get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<R>): Nothing {
        upstream.collect { collector.emit(transform(it)) }
    }
}

/**
 * Creates a [StateFlow] that lazily maps values from this StateFlow without a CoroutineScope.
 */
fun <T, R> StateFlow<T>.mapState(transform: (T) -> R): StateFlow<R> =
    MappedStateFlow(this, transform)
