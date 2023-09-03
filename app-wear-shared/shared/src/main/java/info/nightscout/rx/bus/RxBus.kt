package info.nightscout.rx.bus

import info.nightscout.annotations.OpenForTesting
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.events.Event
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class RxBus @Inject constructor(
    val aapsSchedulers: AapsSchedulers,
    val aapsLogger: AAPSLogger
) {

    private val publisher = PublishSubject.create<Event>()

    fun send(event: Event) {
        aapsLogger.debug(LTag.EVENTS, "Sending $event")
        publisher.onNext(event)
    }

    // Listen should return an Observable and not the publisher
    // Using ofType we filter only events that match that class type
    fun <T : Any> toObservable(eventType: Class<T>): Observable<T> =
        publisher
            .subscribeOn(aapsSchedulers.io)
            .ofType(eventType)
}