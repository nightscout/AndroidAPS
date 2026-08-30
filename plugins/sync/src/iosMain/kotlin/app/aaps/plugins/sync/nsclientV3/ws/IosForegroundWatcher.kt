package app.aaps.plugins.sync.nsclientV3.ws

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.darwin.NSObject

/**
 * Tells the Nightscout connection when iOS will and will not let it hold a websocket.
 *
 * Android keeps the socket alive with a wake lock in a sticky service. iOS has no counterpart: a
 * backgrounded app is suspended, and a websocket with it. So the connection follows the app instead
 * - up while the app is active, down as soon as it is not.
 *
 * Dropping it deliberately is better than letting iOS sever it, because the shared plugin already
 * copes with a dropped socket: `connected` going false starts the REST polling fallback after
 * `wsDisconnectGraceMs`, and `initialLoadFinished` turning false makes the next round backfill the
 * window that was missed. Both were written for connection drops on Android and need nothing new
 * here - backgrounding is just another drop.
 *
 * This is the same shape other iOS followers settled on: a live socket in the foreground, polling
 * behind it.
 *
 * ## Closing before iOS suspends us
 *
 * [onBackground] runs inside a background task assertion, which buys a few seconds to close the
 * socket cleanly instead of having it cut mid-frame. The assertion is always ended, including when
 * the work finishes early - an assertion left open is a way to get the app killed.
 */
class IosForegroundWatcher(
    private val aapsLogger: AAPSLogger,
    private val onForeground: () -> Unit,
    private val onBackground: () -> Unit
) {

    private var observers = mutableListOf<NSObject>()

    fun start() {
        if (observers.isNotEmpty()) return
        observe(UIApplicationDidBecomeActiveNotification) {
            aapsLogger.debug(LTag.NSCLIENT, "App became active, connection may come up")
            onForeground()
        }
        observe(UIApplicationDidEnterBackgroundNotification) {
            aapsLogger.debug(LTag.NSCLIENT, "App went to background, closing the connection")
            closeWithAssertion()
        }
    }

    fun stop() {
        val center = NSNotificationCenter.defaultCenter
        observers.forEach { center.removeObserver(it) }
        observers.clear()
    }

    /** True while iOS is willing to run us. */
    val isForeground: Boolean
        get() = UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive

    private fun observe(name: String?, action: () -> Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = name,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
            usingBlock = { action() }
        )
        observers.add(observer as NSObject)
    }

    /**
     * Closes under a background task assertion.
     *
     * Without one, iOS can suspend the process between the notification and the close, leaving the
     * socket half torn down and the server holding a connection it will time out on its own.
     */
    private fun closeWithAssertion() {
        val application = UIApplication.sharedApplication
        // UIBackgroundTaskInvalid is a macro rather than a symbol, so the sentinel is spelled out.
        var taskId: ULong = TASK_INVALID
        taskId = application.beginBackgroundTaskWithName("aaps-ns-close") {
            // Expiry handler: iOS wants the time back, so give it back before it kills us for it.
            aapsLogger.warn(LTag.NSCLIENT, "Ran out of background time while closing the connection")
            if (taskId != TASK_INVALID) application.endBackgroundTask(taskId)
            taskId = TASK_INVALID
        }
        try {
            onBackground()
        } finally {
            if (taskId != TASK_INVALID) {
                application.endBackgroundTask(taskId)
                taskId = TASK_INVALID
            }
        }
    }

    private companion object {

        /** `UIBackgroundTaskInvalid`, which the bindings do not expose because it is a C macro. */
        const val TASK_INVALID: ULong = 0uL
    }
}
