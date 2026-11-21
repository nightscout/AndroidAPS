package app.aaps.implementation.lifecycle

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import platform.Foundation.NSLock
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSEC_PER_MSEC
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import kotlin.time.TimeSource

/**
 * Asks iOS for time to finish work that started while the app was in the background.
 *
 * A locked iPhone runs the app only when something wakes it. For AAPS that is a Bluetooth event: a CGM reading or
 * keep-alive, or an answer from the pump. About 10 seconds after the event iOS freezes the app again, whatever it is
 * doing. Seen on a real phone on 2026-11-21: a patch connect froze half way and timed out 50 s later on the next wake,
 * and a loop run waited 52 s between its start and its decision.
 *
 * A background task (`beginBackgroundTask`) gets more: up to about 30 seconds while it is held. The calculation, which
 * ends with the loop, holds one through `IosCalculationWakeLock`, and the command queue holds one through
 * `IosCommandExecutionPlatform`. When the time runs out iOS calls the expiration handler, the task is ended there, and
 * the work freezes until the next wake, as before. iOS kills an app that does not end its task in time, so ending it is
 * never left to the caller alone.
 *
 * [install] is called by the app at start. Before that, and always in a test binary, [hold] asks for nothing: there is
 * no `UIApplication` without an app, and the unit tests must not need one.
 */
object IosBackgroundTime {

    private val lock = NSLock()
    private var tasks: BackgroundTasks? = null
    private var logger: AAPSLogger? = null

    /** Called once by the app at start, before anything can run a calculation or a pump command. */
    fun install(aapsLogger: AAPSLogger) {
        lock.lock()
        try {
            logger = aapsLogger
            tasks = UIKitBackgroundTasks
        } finally {
            lock.unlock()
        }
    }

    /** Holds background time for [name] until [BackgroundTimeHold.release], and never longer than [timeoutMs]. */
    fun hold(name: String, timeoutMs: Long): BackgroundTimeHold {
        lock.lock()
        val installed = try {
            tasks to logger
        } finally {
            lock.unlock()
        }
        return BackgroundTimeHold.start(installed.first, installed.second, name, timeoutMs)
    }
}

/** What [IosBackgroundTime] needs from iOS, so the bookkeeping can be tested without an app. */
internal interface BackgroundTasks {

    /** Begins a task, or returns null when iOS gives no time. [onExpire] runs when the time is up. */
    fun begin(name: String, onExpire: () -> Unit): ULong?

    fun end(id: ULong)

    /** Runs [block] after [ms], for the caller's own limit. */
    fun after(ms: Long, block: () -> Unit)
}

/**
 * One piece of held background time.
 *
 * [release] may be called more than once, from any thread, and after the time ran out: the task is ended exactly once,
 * by whichever comes first of the release, the caller's limit and the expiration.
 */
class BackgroundTimeHold private constructor(
    private val tasks: BackgroundTasks?,
    private val logger: AAPSLogger?,
    private val name: String
) {

    private val lock = NSLock()
    private val started = TimeSource.Monotonic.markNow()
    private var id: ULong? = null
    private var ended = false

    fun release() = end("released")

    private fun end(why: String) {
        lock.lock()
        val toEnd = try {
            if (ended) return
            ended = true
            id.also { id = null }
        } finally {
            lock.unlock()
        }
        if (toEnd == null) return
        tasks?.end(toEnd)
        if (why == EXPIRED) logger?.warn(LTag.CORE, "Background time for $name ran out after ${started.elapsedNow()}")
        else logger?.debug(LTag.CORE, "Background time for $name $why after ${started.elapsedNow()}")
    }

    internal companion object {

        private const val EXPIRED = "expired"

        fun start(tasks: BackgroundTasks?, logger: AAPSLogger?, name: String, timeoutMs: Long): BackgroundTimeHold {
            val hold = BackgroundTimeHold(tasks, logger, name)
            if (tasks == null) return hold
            val begun = tasks.begin(name) { hold.end(EXPIRED) }
            if (begun == null) {
                logger?.warn(LTag.CORE, "No background time for $name")
                return hold
            }
            hold.lock.lock()
            try {
                hold.id = begun
            } finally {
                hold.lock.unlock()
            }
            tasks.after(timeoutMs) { hold.end("hit its limit") }
            return hold
        }
    }
}

/**
 * The real tasks. `beginBackgroundTask` and `endBackgroundTask` may be called from any thread; the limit runs on the
 * main queue.
 */
private object UIKitBackgroundTasks : BackgroundTasks {

    override fun begin(name: String, onExpire: () -> Unit): ULong? =
        UIApplication.sharedApplication.beginBackgroundTaskWithName(name, expirationHandler = onExpire)
            .takeIf { it != UIBackgroundTaskInvalid }

    override fun end(id: ULong) = UIApplication.sharedApplication.endBackgroundTask(id)

    override fun after(ms: Long, block: () -> Unit) =
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, ms * NSEC_PER_MSEC.toLong()), dispatch_get_main_queue(), block)
}
