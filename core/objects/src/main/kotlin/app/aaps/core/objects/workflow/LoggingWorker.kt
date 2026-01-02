package app.aaps.core.objects.workflow

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

abstract class LoggingWorker(context: Context, workerParams: WorkerParameters, private val dispatcher: CoroutineDispatcher) : CoroutineWorker(context, workerParams) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }

    override suspend fun doWork(): Result =
        withContext(dispatcher) {
            doWorkAndLog().also {
                aapsLogger.debug(LTag.WORKER, "Worker result ${it::class.java.simpleName.uppercase()} for ${this@LoggingWorker::class.java} ${it.outputData}")
            }
        }

    abstract suspend fun doWorkAndLog(): Result
}