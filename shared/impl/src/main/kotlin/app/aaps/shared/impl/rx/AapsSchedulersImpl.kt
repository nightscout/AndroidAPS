package app.aaps.shared.impl.rx

import app.aaps.core.interfaces.rx.AapsSchedulers
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Singleton

@Singleton
class AapsSchedulersImpl : AapsSchedulers {

    override val main: Scheduler = AndroidSchedulers.mainThread()
    override val io: Scheduler = Schedulers.io()
    override val cpu: Scheduler = Schedulers.computation()
    override val newThread: Scheduler = Schedulers.newThread()
}