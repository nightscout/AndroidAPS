package info.nightscout.androidaps.plugins.bus

import info.nightscout.androidaps.events.Event
import io.reactivex.Observable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class RxBusWrapper @Inject constructor() {

    private val bus: RxBus = RxBus.INSTANCE

    fun send(event: Event) {
        bus.send(event)
    }

    // Listen should return an Observable and not the publisher
    // Using ofType we filter only events that match that class type
    fun <T> toObservable(eventType: Class<T>): Observable<T> =
        bus.toObservable(eventType)
}