package info.nightscout.androidaps.plugins.bus

import info.nightscout.androidaps.events.Event
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class RxBusWrapper @Inject constructor(
    val aapsSchedulers: AapsSchedulers
) {

    private val publisher = PublishSubject.create<Event>()

    fun send(event: Event) {
        publisher.onNext(event)
    }

    // Listen should return an Observable and not the publisher
    // Using ofType we filter only events that match that class type
    fun <T> toObservable(eventType: Class<T>): Observable<T> =
        publisher
            .subscribeOn(aapsSchedulers.io)
            .ofType(eventType)
}