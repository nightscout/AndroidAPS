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
 * ## Not wired to anything, and do not wire it as written
 *
 * Nothing constructs this class. That matters more than it looks, because the design it describes
 * rests on a fallback that does not exist.
 *
 * The claim used to be that dropping the socket deliberately is safe, since `connected` going false
 * starts a REST polling fallback after `wsDisconnectGraceMs`. There is no such fallback. The
 * five-minute tick in `NSClientV3Plugin` runs a load only when websockets are switched off or the
 * platform has none; while they are on it logs and does nothing. `wsDisconnectGraceMs` debounces the
 * `masterReachable` flow and starts no load.
 *
 * What is true is the other half: `initialLoadFinished` turning false makes the next round backfill
 * the missed window. But that round is started by a websocket *connect*. So closing the socket on
 * backgrounding, with nothing to reopen it, would remove the only recovery path there is rather than
 * hand over to a second one.
 *
 * If this is ever wired up, [onForeground] is the valuable half - a reconnect check when the app
 * comes back. [onBackground] should not close the socket until something exists that reliably
 * reopens it: a shared watchdog that forces a load when `connected` has been false for too long, or
 * a real polling fallback.
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
