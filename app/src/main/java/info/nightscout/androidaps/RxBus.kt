package info.nightscout.androidaps

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject

// Use object so we have a singleton instance
object RxBus {

    private val publisher = PublishSubject.create<Any>()

    fun send(event: Any) {
        publisher.onNext(event)
    }

    fun toObservable(): Observable<Any> = publisher

    // Listen should return an Observable and not the publisher
    // Using ofType we filter only events that match that class type
    fun <T> toObservable(eventType: Class<T>): Observable<T> = publisher.ofType(eventType)
}
