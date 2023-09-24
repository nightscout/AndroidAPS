package info.nightscout.androidaps.interaction.utils

import android.os.SystemClock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import info.nightscout.androidaps.BuildConfig
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created for xDrip by jamorham on 07/03/2018
 * Adapted for AAPS by dlvoy on 2019-11-11
 *
 * Tasks which are fired from events can be scheduled here and only execute when they become idle
 * and are not being rescheduled within their wait window.
 *
 */
@Singleton
class Inevitable @Inject internal constructor() {

    @Inject lateinit var wearUtil: WearUtil
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var dateUtil: DateUtil

    private val tasks = ConcurrentHashMap<String, Task>()
    fun task(id: String, idle_for: Long, runnable: Runnable?) {
        if (idle_for > MAX_QUEUE_TIME) {
            throw RuntimeException("$id Requested time: $idle_for beyond max queue time")
        }
        val task = tasks[id]
        if (task != null) {
            // if it already exists then extend the time
            task.extendTime(idle_for)
            if (debug) aapsLogger.debug(LTag.WEAR, "Extending time for: " + id + " to " + dateUtil.dateAndTimeAndSecondsString(task.`when`))
        } else {
            // otherwise create new task
            if (runnable == null) return  // extension only if already exists
            tasks[id] = Task(id, idle_for, runnable)
            if (debug) {
                aapsLogger.debug(
                    LTag.WEAR,
                    "Creating task: " + id + " due: " + dateUtil.dateAndTimeAndSecondsString(tasks.getValue(id).`when`)
                )
            }

            // create a thread to wait and execute in background
            val t = Thread {
                val wl = wearUtil.getWakeLock(id, MAX_QUEUE_TIME + 5000)
                try {
                    var running = true
                    // wait for task to be due or killed
                    while (running) {
                        SystemClock.sleep(500)
                        val thisTask = tasks[id]
                        running = thisTask != null && !thisTask.poll()
                    }
                } finally {
                    wearUtil.releaseWakeLock(wl)
                }
            }
            t.priority = Thread.MIN_PRIORITY
            t.start()
        }
    }

    fun kill(id: String) {
        tasks.remove(id)
    }

    private inner class Task(private val id: String, offset: Long, private val what: Runnable) {

        var `when`: Long = 0
        fun extendTime(offset: Long) {
            `when` = wearUtil.timestamp() + offset
        }

        fun poll(): Boolean {
            val till = wearUtil.msTill(`when`)
            if (till < 1) {
                if (debug) aapsLogger.debug(LTag.WEAR, "Executing task! $id")
                tasks.remove(id) // early remove to allow overlapping scheduling
                what.run()
                return true
            } else if (till > MAX_QUEUE_TIME) {
                aapsLogger.debug(LTag.WEAR, "Task: $id In queue too long: $till")
                tasks.remove(id)
                return true
            }
            return false
        }

        init {
            extendTime(offset)
        }
    }

    companion object {

        private const val MAX_QUEUE_TIME = Constants.MINUTE_IN_MS.toInt() * 6
        private val debug = BuildConfig.DEBUG
    }
}