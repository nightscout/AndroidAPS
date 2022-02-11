package info.nightscout.androidaps.plugins.pump.eopatch.extension

import io.reactivex.Maybe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

fun <T> Maybe<T>.observeOnMainThread(): Maybe<T> = observeOn(AndroidSchedulers.mainThread())

fun <T> Maybe<T>.observeOnComputation(): Maybe<T> = observeOn(Schedulers.computation())

fun <T> Maybe<T>.observeOnIo(): Maybe<T> = observeOn(Schedulers.io())

fun <T> Maybe<T>.subscribeEmpty(): Disposable = subscribe({}, {}, {})

fun <T> Maybe<T>.subscribeEmpty(onSuccess: (T) -> Unit): Disposable = subscribe(onSuccess, {}, {})

fun <T> Maybe<T>.subscribeEmpty(onSuccess: (T) -> Unit, onError: (Throwable) -> Unit): Disposable = subscribe(onSuccess, onError, {})

fun <T> Maybe<T>.subscribeDefault(): Disposable = subscribe({ Timber.d("onSuccess") }, { Timber.e(it, "onError") }, { Timber.d("onComplete") })

fun <T> Maybe<T>.subscribeDefault(onSuccess: (T) -> Unit): Disposable = subscribe(onSuccess, { Timber.e(it, "onError") }, { Timber.d("onComplete") })

fun <T> Maybe<T>.subscribeDefault(onSuccess: (T) -> Unit, onError: (Throwable) -> Unit): Disposable = subscribe(onSuccess, onError, { Timber.d("onComplete") })

fun <T> Maybe<T>.with(): Maybe<T> = subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
