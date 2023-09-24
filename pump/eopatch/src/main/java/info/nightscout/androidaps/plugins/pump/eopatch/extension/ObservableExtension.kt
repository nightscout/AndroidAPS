package info.nightscout.androidaps.plugins.pump.eopatch.extension

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

fun <T : Any> Observable<T>.observeOnMainThread(): Observable<T> = observeOn(AndroidSchedulers.mainThread())

fun <T : Any> Observable<T>.observeOnComputation(): Observable<T> = observeOn(Schedulers.computation())

fun <T : Any> Observable<T>.observeOnIo(): Observable<T> = observeOn(Schedulers.io())

fun <T : Any> Observable<T>.subscribeEmpty(): Disposable = subscribe({}, {}, {})

fun <T : Any> Observable<T>.subscribeEmpty(onSuccess: (T) -> Unit): Disposable = subscribe(onSuccess, {}, {})

fun <T : Any> Observable<T>.subscribeEmpty(onSuccess: (T) -> Unit, onError: (Throwable) -> Unit): Disposable = subscribe(onSuccess, onError, {})

fun <T : Any> Observable<T>.subscribeDefault(aapsLogger: AAPSLogger): Disposable = subscribe({ aapsLogger.debug(LTag.PUMP, "onSuccess") }, { aapsLogger.error(LTag.PUMP, "onError", it) }, {
    aapsLogger.debug(LTag.PUMP, "onComplete")
})

fun <T : Any> Observable<T>.subscribeDefault(aapsLogger: AAPSLogger, onSuccess: (T) -> Unit): Disposable =
    subscribe(onSuccess, { aapsLogger.error(LTag.PUMP, "onError", it) }, { aapsLogger.debug(LTag.PUMP, "onComplete") })

fun <T : Any> Observable<T>.subscribeDefault(aapsLogger: AAPSLogger, onSuccess: (T) -> Unit, onError: (Throwable) -> Unit): Disposable =
    subscribe(onSuccess, onError, { aapsLogger.debug(LTag.PUMP, "onComplete") })

fun <T : Any> Observable<T>.with(): Observable<T> = subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())