package info.nightscout.androidaps.utils.rx

import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * Created by adrian on 12.04.20.
 */

interface AapsSchedulers {
    val main: Scheduler
    val io: Scheduler
    val cpu: Scheduler
}

class DefaultAapsSchedulers : AapsSchedulers {
    override val main: Scheduler = AndroidSchedulers.mainThread()
    override val io: Scheduler = Schedulers.io()
    override val cpu: Scheduler = Schedulers.computation()
}

class TestAapsSchedulers : AapsSchedulers {
    override val main: Scheduler = Schedulers.trampoline()
    override val io: Scheduler = Schedulers.trampoline()
    override val cpu: Scheduler = Schedulers.trampoline()
}