package info.nightscout.androidaps.plugins.bus

import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.androidaps.events.Event
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
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
    fun <T> toObservable(eventType: Class<T>): Observable<T> =
        publisher
            .subscribeOn(aapsSchedulers.io)
            .ofType(eventType)
}