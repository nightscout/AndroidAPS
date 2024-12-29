package app.aaps.pump.eopatch

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject

object EoPatchRxBus {

    private val publishSubject: PublishSubject<Any> = PublishSubject.create()

    fun publish(event: Any) {
        publishSubject.onNext(event)
    }

    fun <T : Any> listen(eventType: Class<T>): Observable<T> {
        return publishSubject.ofType(eventType)
    }

}