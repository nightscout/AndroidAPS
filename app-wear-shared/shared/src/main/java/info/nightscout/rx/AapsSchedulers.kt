package info.nightscout.rx

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.schedulers.Schedulers

/**
 * Created by adrian on 12.04.20.
 */

interface AapsSchedulers {
    val main: Scheduler
    val io: Scheduler
    val cpu: Scheduler
    val newThread: Scheduler
}

class DefaultAapsSchedulers : AapsSchedulers {
    override val main: Scheduler = AndroidSchedulers.mainThread()
    override val io: Scheduler = Schedulers.io()
    override val cpu: Scheduler = Schedulers.computation()
    override val newThread: Scheduler = Schedulers.newThread()
}

class TestAapsSchedulers : AapsSchedulers {
    override val main: Scheduler = Schedulers.trampoline()
    override val io: Scheduler = Schedulers.trampoline()
    override val cpu: Scheduler = Schedulers.trampoline()
    override val newThread: Scheduler = Schedulers.trampoline()
}