package info.nightscout.core.utils.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

abstract class LoggingWorker(context: Context, workerParams: WorkerParameters, private val dispatcher: CoroutineDispatcher) : CoroutineWorker(context, workerParams) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy

    init {
        @Suppress("LeakingThis")
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }

    override suspend fun doWork(): Result =
        try {
            withContext(dispatcher) {
                doWorkAndLog().also {
                    aapsLogger.debug(LTag.WORKER, "Worker result ${it::class.java.simpleName.uppercase()} for ${this::class.java}")
                }
            }
        } catch (e: Exception) {
            fabricPrivacy.logException(e)
            e.printStackTrace()
            Result.failure(workDataOf("Error" to e.localizedMessage))
        }

    abstract suspend fun doWorkAndLog(): Result
}