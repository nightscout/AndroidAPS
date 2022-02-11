package info.nightscout.androidaps.plugins.pump.eopatch.extension

import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

fun <T> Single<T>.observeOnMainThread(): Single<T> = observeOn(AndroidSchedulers.mainThread())

fun <T> Single<T>.subscribeDefault(onSuccess: (T) -> Unit): Disposable = subscribe(onSuccess, {
    Timber.e(it, "onError")
})

fun <T> Single<T>.with(): Single<T> = subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
