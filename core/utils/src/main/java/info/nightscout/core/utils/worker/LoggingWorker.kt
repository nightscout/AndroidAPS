package info.nightscout.core.utils.worker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.android.HasAndroidInjector
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import javax.inject.Inject

abstract class LoggingWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    @Inject lateinit var aapsLogger: AAPSLogger

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }

    override fun doWork(): Result =
        doWorkAndLog().also {
            aapsLogger.debug(LTag.WORKER, "Worker result ${it::class.java.simpleName.uppercase()} for ${this::class.java}")
        }

    abstract fun doWorkAndLog(): Result
}