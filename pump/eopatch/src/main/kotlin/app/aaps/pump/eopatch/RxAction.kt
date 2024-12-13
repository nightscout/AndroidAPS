@file:Suppress("unused")

package app.aaps.pump.eopatch

import android.os.SystemClock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.AapsSchedulers
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
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

    @JvmOverloads
    fun runOnMainThread(action: Runnable, delayMs: Long = 0) {
        single(action, delayMs, aapsSchedulers.main)
            .subscribe({

                       },
                       { e ->
                           aapsLogger.error("SilentObserver.onError() ignore", e)
                       })
    }
}
