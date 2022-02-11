package info.nightscout.androidaps.plugins.pump.eopatch

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject

object EoPatchRxBus {
    private val publishSubject: PublishSubject<Any> = PublishSubject.create()

    fun publish(event: Any) {
        publishSubject.onNext(event)
    }

    fun <T> listen(eventType: Class<T>): Observable<T> {
        return publishSubject.ofType(eventType)
    }

}