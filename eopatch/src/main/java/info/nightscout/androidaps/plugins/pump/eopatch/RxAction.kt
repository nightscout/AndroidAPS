package info.nightscout.androidaps.plugins.pump.eopatch

import io.reactivex.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.reactivestreams.Subscription
import timber.log.Timber
import java.util.concurrent.TimeUnit

enum class RxVoid {
    INSTANCE
}

class SilentObserver<T> : MaybeObserver<T>, SingleObserver<T>, Observer<T>, FlowableSubscriber<T> {
    override fun onSubscribe(d: Disposable) {}
    override fun onSuccess(t: T) {}
    override fun onError(e: Throwable) = Timber.d(e, "SilentObserver.onError() ignore")
    override fun onComplete() {}
    override fun onNext(t: T) {}
    override fun onSubscribe(s: Subscription) {}
}

object RxAction {
    private fun msleep(millis: Long) {
        if (millis <= 0)
            return
        try {
            Thread.sleep(millis)
        } catch (e: InterruptedException) {
        }

    }

    private fun delay(delayMs: Long): Single<*> {
        return if (delayMs <= 0) {
            Single.just(1)
        } else Single.timer(delayMs, TimeUnit.MILLISECONDS)

    }

    fun single(action: Runnable, delayMs: Long, scheduler: Scheduler): Single<*> {
        return delay(delayMs)
                .observeOn(scheduler)
                .flatMap { o ->
                    Single.fromCallable {
                        action.run()
                        RxVoid.INSTANCE
                    }
                }
    }

    fun safeSingle(action: Runnable, delayMs: Long, scheduler: Scheduler): Single<*> {
        return single(action, delayMs, scheduler)
    }

    @JvmOverloads
    fun runOnComputationThread(action: Runnable, delayMs: Long = 0) {
        single(action, delayMs, Schedulers.computation()).subscribe(SilentObserver())
    }

    @JvmOverloads
    fun runOnIoThread(action: Runnable, delayMs: Long = 0) {
        single(action, delayMs, Schedulers.io()).subscribe(SilentObserver())
    }

    @JvmOverloads
    fun runOnNewThread(action: Runnable, delayMs: Long = 0) {
        single(action, delayMs, Schedulers.newThread()).subscribe(SilentObserver())
    }

    @JvmOverloads
    fun runOnMainThread(action: Runnable, delayMs: Long = 0) {
        single(action, delayMs, AndroidSchedulers.mainThread()).subscribe(SilentObserver())
    }

    @JvmOverloads
    fun safeRunOnComputationThread(action: Runnable, delayMs: Long = 0) {
        safeSingle(action, delayMs, Schedulers.computation()).subscribe(SilentObserver())
    }

    @JvmOverloads
    fun safeRunOnIoThread(action: Runnable, delayMs: Long = 0) {
        safeSingle(action, delayMs, Schedulers.io()).subscribe(SilentObserver())
    }

    @JvmOverloads
    fun safeRunOnNewThread(action: Runnable, delayMs: Long = 0) {
        safeSingle(action, delayMs, Schedulers.newThread()).subscribe(SilentObserver())
    }

    @JvmOverloads
    fun safeRunOnMainThread(action: Runnable, delayMs: Long = 0) {
        safeSingle(action, delayMs, AndroidSchedulers.mainThread()).subscribe(SilentObserver())
    }


    fun singleOnMainThread(action: Runnable, delayMs: Long): Single<*> {
        return single(action, delayMs, AndroidSchedulers.mainThread())
    }

    fun singleOnComputationThread(action: Runnable, delayMs: Long): Single<*> {
        return single(action, delayMs, Schedulers.computation())
    }

    fun singleOnIoThread(action: Runnable, delayMs: Long): Single<*> {
        return single(action, delayMs, Schedulers.io())
    }

    fun singleOnNewThread(action: Runnable, delayMs: Long): Single<*> {
        return single(action, delayMs, Schedulers.newThread())
    }
}
