package info.nightscout.androidaps.plugins.pump.eopatch.extension

import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

fun Completable.observeOnMainThread(): Completable = observeOn(AndroidSchedulers.mainThread())

fun Completable.observeOnComputation(): Completable = observeOn(Schedulers.computation())

fun Completable.observeOnIo(): Completable = observeOn(Schedulers.io())

fun Completable.subscribeEmpty(): Disposable {
    return subscribe({}, {})
}

fun Completable.subscribeEmpty(onComplete: () -> Unit, onError: (Throwable) -> Unit): Disposable {
    return subscribe(onComplete, onError)
}

fun Completable.subscribeDefault(): Disposable {
    return subscribe({ Timber.d("onComplete") }, { Timber.e(it, "onError") })
}

fun Completable.subscribeDefault(onComplete: () -> Unit): Disposable {
    return subscribe(onComplete, { Timber.e(it, "onError") })
}
