package app.aaps.shared.impl.rx.bus

import app.aaps.annotations.OpenForTesting
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class RxBusImpl @Inject constructor(
    val aapsSchedulers: AapsSchedulers,
    val aapsLogger: AAPSLogger
) : RxBus {

    private val publisher = PublishSubject.create<Event>()

    override fun send(event: Event) {
        aapsLogger.debug(LTag.EVENTS, "Sending $event")
        publisher.onNext(event)
    }

    // Listen should return an Observable and not the publisher
    // Using ofType we filter only events that match that class type
    override fun <T : Any> toObservable(eventType: Class<T>): Observable<T> =
        publisher
            .subscribeOn(aapsSchedulers.io)
            .ofType(eventType)
}