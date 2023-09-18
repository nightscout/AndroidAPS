package info.nightscout.rx

import io.reactivex.rxjava3.core.Scheduler

/**
 * Created by adrian on 12.04.20.
 */

interface AapsSchedulers {

    val main: Scheduler
    val io: Scheduler
    val cpu: Scheduler
    val newThread: Scheduler
}