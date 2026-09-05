package app.aaps.pump.eopatch.extension

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable

fun <T : Any> Observable<T>.subscribeEmpty(): Disposable = subscribe({}, {}, {})

fun <T : Any> Observable<T>.subscribeDefault(aapsLogger: AAPSLogger, onSuccess: (T) -> Unit): Disposable =
    subscribe(onSuccess, { aapsLogger.error(LTag.PUMP, "onError", it) }, { aapsLogger.debug(LTag.PUMP, "onComplete") })
