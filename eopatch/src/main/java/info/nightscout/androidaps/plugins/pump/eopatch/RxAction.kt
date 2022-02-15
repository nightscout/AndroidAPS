@file:Suppress("unused")

package info.nightscout.androidaps.plugins.pump.eopatch

import android.os.SystemClock
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.logging.AAPSLogger
import io.reactivex.*
import io.reactivex.disposables.Disposable
import org.reactivestreams.Subscription
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RxAction @Inject constructor(
    private val aapsSchedulers: AapsSchedulers,
    private val aapsLogger: AAPSLogger
) {

    enum class RxVoid {
        INSTANCE
    }

    class SilentObserver<T>(private val aapsLogger: AAPSLogger) : MaybeObserver<T>, SingleObserver<T>, Observer<T>, FlowableSubscriber<T> {

        override fun onSubscribe(d: Disposable) {}
        override fun onSuccess(t: T) {}
        override fun onError(e: Throwable) = aapsLogger.error("SilentObserver.onError() ignore", e)
        override fun onComplete() {}
        override fun onNext(t: T) {}
        override fun onSubscribe(s: Subscription) {}
    }

    private fun sleep(millis: Long) {
        if (millis <= 0)
            return
        SystemClock.sleep(millis)
    }

    private fun delay(delayMs: Long): Single<*> {
        return if (delayMs <= 0) {
            Single.just(1)
        } else Single.timer(delayMs, TimeUnit.MILLISECONDS)

    }

    fun single(action: Runnable, delayMs: Long, scheduler: Scheduler): Single<*> {
        return delay(delayMs)
            .observeOn(scheduler)
            .flatMap {
                Single.fromCallable {
                    action.run()
                    RxVoid.INSTANCE
                }
            }
    }

    private fun safeSingle(action: Runnable, delayMs: Long, scheduler: Scheduler): Single<*> {
        return single(action, delayMs, scheduler)
    }

    @JvmOverloads
    fun runOnComputationThread(action: Runnable, delayMs: Long = 0) {
        single(action, delayMs, aapsSchedulers.cpu).subscribe(SilentObserver(aapsLogger))
    }

    @JvmOverloads
    fun runOnIoThread(action: Runnable, delayMs: Long = 0) {
        single(action, delayMs, aapsSchedulers.io).subscribe(SilentObserver(aapsLogger))
    }

    @JvmOverloads
    fun runOnNewThread(action: Runnable, delayMs: Long = 0) {
        single(action, delayMs, aapsSchedulers.newThread).subscribe(SilentObserver(aapsLogger))
    }

    @JvmOverloads
    fun runOnMainThread(action: Runnable, delayMs: Long = 0) {
        single(action, delayMs, aapsSchedulers.main).subscribe(SilentObserver(aapsLogger))
    }

    @JvmOverloads
    fun safeRunOnComputationThread(action: Runnable, delayMs: Long = 0) {
        safeSingle(action, delayMs, aapsSchedulers.cpu).subscribe(SilentObserver(aapsLogger))
    }

    @JvmOverloads
    fun safeRunOnIoThread(action: Runnable, delayMs: Long = 0) {
        safeSingle(action, delayMs, aapsSchedulers.io).subscribe(SilentObserver(aapsLogger))
    }

    @JvmOverloads
    fun safeRunOnNewThread(action: Runnable, delayMs: Long = 0) {
        safeSingle(action, delayMs, aapsSchedulers.newThread).subscribe(SilentObserver(aapsLogger))
    }

    @JvmOverloads
    fun safeRunOnMainThread(action: Runnable, delayMs: Long = 0) {
        safeSingle(action, delayMs, aapsSchedulers.main).subscribe(SilentObserver(aapsLogger))
    }

    fun singleOnMainThread(action: Runnable, delayMs: Long): Single<*> {
        return single(action, delayMs, aapsSchedulers.main)
    }

    fun singleOnComputationThread(action: Runnable, delayMs: Long): Single<*> {
        return single(action, delayMs, aapsSchedulers.cpu)
    }

    fun singleOnIoThread(action: Runnable, delayMs: Long): Single<*> {
        return single(action, delayMs, aapsSchedulers.io)
    }

    fun singleOnNewThread(action: Runnable, delayMs: Long): Single<*> {
        return single(action, delayMs, aapsSchedulers.newThread)
    }
}
