package app.aaps.pump.common.hw.rileylink.service.tasks

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceTaskExecutor @Inject constructor() : ThreadPoolExecutor(1, 1, 10000, TimeUnit.MILLISECONDS, taskQueue) {

    @Inject lateinit var aapsLogger: AAPSLogger

    companion object {

        private val taskQueue = LinkedBlockingQueue<Runnable>()
    }

    fun startTask(task: ServiceTask): ServiceTask {
        execute(task) // task will be run on async thread from pool.
        return task
    }

    override fun beforeExecute(t: Thread, r: Runnable) {
        // This is run on either caller UI thread or Service UI thread.
        val task = r as ServiceTask
        aapsLogger.debug(LTag.PUMPBTCOMM, "About to run task ${task.javaClass.simpleName}")
        task.preOp()
    }

    override fun afterExecute(r: Runnable, t: Throwable?) {
        // This is run on either caller UI thread or Service UI thread.
        val task = r as ServiceTask
        task.postOp()
        aapsLogger.debug(LTag.PUMPBTCOMM, "Finishing task ${task.javaClass.simpleName}")
    }
}