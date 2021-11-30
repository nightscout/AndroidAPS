package info.nightscout.androidaps.utils.rx

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.Single
import java.util.concurrent.TimeUnit
import kotlin.math.pow

/**
 * Created by adrian on 12.04.20.
 */

inline fun <reified T> Single<T>.retryExponentialBackoff(retries: Int, time: Long, timeUnit: TimeUnit): Single<T> =
    this.retryWhen { throwables: Flowable<Throwable> ->
        throwables.zipWith(
            Flowable.range(0, retries),
            { throwable: Throwable, retryCount: Int ->
                if (retryCount >= retries) {
                    throw throwable
                } else {
                    retryCount
                }
            }
        ).flatMap { retryCount: Int ->
            Flowable.timer(time * 2.toDouble().pow(retryCount.toDouble()).toLong(), timeUnit)
        }
    }

fun Completable.retryExponentialBackoff(retries: Int, time: Long, timeUnit: TimeUnit): Completable =
    this.retryWhen { throwables: Flowable<Throwable> ->
        throwables.zipWith(
            Flowable.range(0, retries),
            { throwable: Throwable, retryCount: Int ->
                if (retryCount >= retries) {
                    throw throwable
                } else {
                    retryCount
                }
            }
        ).flatMap { retryCount: Int ->
            Flowable.timer(time * 2.toDouble().pow(retryCount.toDouble()).toLong(), timeUnit)
        }
    }

inline fun <reified T> Observable<T>.retryWithBackoff(
    retries: Int,
    delay: Long,
    timeUnit: TimeUnit,
    delayFactor: Double = 1.0
): Observable<T> = this.retryWhen {
    it.zipWith(Observable.range(0, retries + 1), { throwable: Throwable, count: Int ->
        if (count >= retries) {
            throw throwable
        } else {
            count
        }
    }).flatMap { retryCount: Int ->
        val actualDelay = (timeUnit.toMillis(delay) * delayFactor.pow(retryCount.toDouble())).toLong()
        Observable.timer(actualDelay, TimeUnit.MILLISECONDS)
    }
}