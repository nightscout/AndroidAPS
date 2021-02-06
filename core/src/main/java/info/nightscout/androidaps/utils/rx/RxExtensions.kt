package info.nightscout.androidaps.utils.rx

import io.reactivex.Completable
import io.reactivex.Flowable
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