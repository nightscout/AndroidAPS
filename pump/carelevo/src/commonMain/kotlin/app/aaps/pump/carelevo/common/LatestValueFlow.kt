package app.aaps.pump.carelevo.common

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * A stream that keeps its last value and gives it to every new collector.
 *
 * Unlike a `StateFlow` it has no value before the first one, and it sends every value, also one equal to the last.
 * That is what the RxJava `BehaviorSubject`s it replaces did. Sending never waits. A collector that is behind keeps up to
 * [LATEST_VALUE_BUFFER] values, as `observeOn` queued them; only beyond that does it lose the oldest.
 */
fun <T> latestValueFlow(): MutableSharedFlow<T> =
    MutableSharedFlow(replay = 1, extraBufferCapacity = LATEST_VALUE_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)

/** The last value sent, or null before the first one. */
val <T> SharedFlow<T>.latest: T?
    get() = replayCache.lastOrNull()

/** Values kept for a collector that is behind, besides the last one. */
const val LATEST_VALUE_BUFFER = 64
