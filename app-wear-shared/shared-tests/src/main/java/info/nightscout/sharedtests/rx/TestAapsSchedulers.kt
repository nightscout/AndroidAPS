package info.nightscout.sharedtests.rx

import info.nightscout.rx.AapsSchedulers
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.schedulers.Schedulers

/**
 * Created by adrian on 12.04.20.
 */

class TestAapsSchedulers : AapsSchedulers {

    override val main: Scheduler = Schedulers.trampoline()
    override val io: Scheduler = Schedulers.trampoline()
    override val cpu: Scheduler = Schedulers.trampoline()
    override val newThread: Scheduler = Schedulers.trampoline()
}