package app.aaps.core.interfaces.rx

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest

/**
 * Groups items into batches and emits a batch once nothing new arrived for [quietPeriodMs].
 *
 * This is the Flow replacement for the RxJava idiom
 * `publish { shared -> shared.buffer(shared.debounce(quietPeriod)) }`, which we used to coalesce
 * bursts of events (for example the Wear Data Layer replaying its queue after a reconnect). The
 * timer stays idle while nothing arrives, unlike a fixed window, so a single event is still
 * delivered after one quiet period instead of waiting for a window boundary.
 *
 * `transformLatest` cancels and joins the previous block before it starts the new one, so the batch
 * is only ever touched by one coroutine and needs no lock: every new item restarts the [delay], and
 * the block reaches the emit only after a full quiet period.
 *
 * A batch that is waiting for its quiet period is dropped if the collector is cancelled. That is
 * what the RxJava version did when its subscription was disposed, and it keeps items from being
 * stored twice.
 *
 * @param quietPeriodMs how long the source has to stay silent before the batch is emitted
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.chunkedOnQuietPeriod(quietPeriodMs: Long): Flow<List<T>> = flow {
    // Built inside flow { } so every collection of the result gets its own batch.
    val batch = mutableListOf<T>()
    transformLatest { item ->
        batch += item
        delay(quietPeriodMs)
        val complete = batch.toList()
        batch.clear()
        emit(complete)
    }.collect { emit(it) }
}
