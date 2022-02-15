package info.nightscout.androidaps.plugins.pump.eopatch.extension

import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

fun <T> Observable<T>.observeOnMainThread(): Observable<T> = observeOn(AndroidSchedulers.mainThread())

fun <T> Observable<T>.observeOnComputation(): Observable<T> = observeOn(Schedulers.computation())

fun <T> Observable<T>.observeOnIo(): Observable<T> = observeOn(Schedulers.io())

fun <T> Observable<T>.subscribeEmpty(): Disposable = subscribe({}, {}, {})

fun <T> Observable<T>.subscribeEmpty(onSuccess: (T) -> Unit): Disposable = subscribe(onSuccess, {}, {})

fun <T> Observable<T>.subscribeEmpty(onSuccess: (T) -> Unit, onError: (Throwable) -> Unit): Disposable = subscribe(onSuccess, onError, {})

fun <T> Observable<T>.subscribeDefault(aapsLogger: AAPSLogger): Disposable = subscribe({ aapsLogger.debug(LTag.PUMP, "onSuccess") }, { aapsLogger.error(LTag.PUMP, "onError", it) }, {
    aapsLogger.debug(LTag.PUMP, "onComplete")
})

fun <T> Observable<T>.subscribeDefault(aapsLogger: AAPSLogger, onSuccess: (T) -> Unit): Disposable =
    subscribe(onSuccess, { aapsLogger.error(LTag.PUMP, "onError", it) }, { aapsLogger.debug(LTag.PUMP, "onComplete") })

fun <T> Observable<T>.subscribeDefault(aapsLogger: AAPSLogger, onSuccess: (T) -> Unit, onError: (Throwable) -> Unit): Disposable =
    subscribe(onSuccess, onError, { aapsLogger.debug(LTag.PUMP, "onComplete") })

fun <T> Observable<T>.with(): Observable<T> = subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())