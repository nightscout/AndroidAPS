package app.aaps.shared.impl.rx.bus

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventIobCalculationProgress
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewCalcProgress
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RxBusImpl @Inject constructor(
    val aapsSchedulers: AapsSchedulers,
    val aapsLogger: AAPSLogger
) : RxBus {

    private val publisher = PublishSubject.create<Event>()
    private val flowPublisher = MutableSharedFlow<Event>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun send(event: Event) {
        if (event !is EventIobCalculationProgress && event !is EventUpdateOverviewCalcProgress)
            aapsLogger.debug(LTag.EVENTS, "Sending $event")
        publisher.onNext(event)
        flowPublisher.tryEmit(event)
    }

    // Listen should return an Observable and not the publisher
    // Using ofType we filter only events that match that class type
    override fun <T : Any> toObservable(eventType: Class<T>): Observable<T> =
        publisher
            .subscribeOn(aapsSchedulers.io)
            .ofType(eventType)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Event> toFlow(eventType: Class<T>): Flow<T> =
        flowPublisher
            .filter { eventType.isInstance(it) }
            .map { it as T }
}