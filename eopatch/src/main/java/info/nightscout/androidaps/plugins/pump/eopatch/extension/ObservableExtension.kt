package info.nightscout.androidaps.plugins.pump.eopatch.extension

import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

fun <T> Observable<T>.observeOnMainThread(): Observable<T> = observeOn(AndroidSchedulers.mainThread())

fun <T> Observable<T>.observeOnComputation(): Observable<T> = observeOn(Schedulers.computation())

fun <T> Observable<T>.observeOnIo(): Observable<T> = observeOn(Schedulers.io())

fun <T> Observable<T>.subscribeEmpty(): Disposable = subscribe({}, {}, {})

fun <T> Observable<T>.subscribeEmpty(onSuccess: (T) -> Unit): Disposable = subscribe(onSuccess, {}, {})

fun <T> Observable<T>.subscribeEmpty(onSuccess: (T) -> Unit, onError: (Throwable) -> Unit): Disposable = subscribe(onSuccess, onError, {})

fun <T> Observable<T>.subscribeDefault(): Disposable = subscribe({ Timber.d("onSuccess") }, { Timber.e(it, "onError") }, { Timber.d("onComplete") })

fun <T> Observable<T>.subscribeDefault(onSuccess: (T) -> Unit): Disposable = subscribe(onSuccess, { Timber.e(it, "onError") }, { Timber.d("onComplete") })

fun <T> Observable<T>.subscribeDefault(onSuccess: (T) -> Unit, onError: (Throwable) -> Unit): Disposable = subscribe(onSuccess, onError, { Timber.d("onComplete") })

fun <T> Observable<T>.with(): Observable<T> = subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())