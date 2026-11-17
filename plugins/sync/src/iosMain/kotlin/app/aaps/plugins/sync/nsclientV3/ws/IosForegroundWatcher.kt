package app.aaps.plugins.sync.nsclientV3.ws

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import platform.Foundation.NSDate
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.darwin.NSObject

/**
 * Tells the app when iOS has stopped running it for long enough that its Nightscout data is behind.
 *
 * Android keeps the client running in the background, so its database stays current and a socket
 * that drops is noticed at once. iOS suspends the whole process instead. Nothing is delivered while
 * it sleeps, and the websocket comes back as a zombie: no close event was ever seen, so
 * `NsConnection.connected` is still true, `NSClientV3Plugin.needsPolling` is still false, and the
 * five minute tick logs a tick and loads nothing. The only recovery is the catch-up round that a
 * websocket **connect** starts, so until the transport's own ping times out, minutes later, nothing
 * at all happens. That is the "glucose stays 23 minutes old for two minutes after opening the app"
 * half of nightscout/AndroidAPS#5495.
 *
 * This watcher only reports the gap. What to do about it belongs to the caller, which replaces the
 * connection and pre-snoozes the stale-data alarm - see `AapsAppHost`.
 *
 * ## Why it only watches, and no longer closes
 *
 * An earlier version of this class also closed the socket on backgrounding, and its own
 * documentation said not to wire it up, because closing deliberately would remove the only recovery
 * path there is: there is no REST polling fallback behind the websocket. That half is gone. Leaving
 * the socket open costs nothing while the process is suspended - iOS is not running it either way -
 * and the connect that follows a [onResumeAfterGap] is what brings the missed window back.
 *
 * ## Which notifications, and why not the obvious ones
 *
 * `DidEnterBackground` and `WillEnterForeground`, never `WillResignActive` / `DidBecomeActive`. The
 * active pair also fires for a system sheet, the control centre and the notification shade, none of
 * which stop the process, so they would report a gap that never happened and reconnect for nothing.
 * `IosProtectionLifecycle` and the unlock gate in `AapsAppHost` make the same distinction, for the
 * same reason.
 *
 * [leftAt] starts at construction rather than at zero because iOS can launch an app straight into
 * the background and freeze it there, before any notification arrives. Without that, the first real
 * appearance - possibly long after launch - would not count as a gap.
 */
class IosForegroundWatcher(
    private val aapsLogger: AAPSLogger,
    private val onResumeAfterGap: (awayMs: Long) -> Unit,
    private val gapMs: Long = DEFAULT_GAP_MS,
    private val now: () -> Long = { (NSDate().timeIntervalSince1970 * 1000.0).toLong() }
) {

    private val observers = mutableListOf<NSObject>()

    /** When the app was last known to be running. */
    private var leftAt: Long = now()

    fun start() {
        if (observers.isNotEmpty()) return
        observe(UIApplicationDidEnterBackgroundNotification) { onBackground() }
        observe(UIApplicationWillEnterForegroundNotification) { onForeground() }
    }

    fun stop() {
        val center = NSNotificationCenter.defaultCenter
        observers.forEach { center.removeObserver(it) }
        observers.clear()
    }

    /** Visible for testing: the notification bodies, without a real notification centre. */
    internal fun onBackground() {
        leftAt = now()
    }

    internal fun onForeground() {
        val awayMs = now() - leftAt
        leftAt = now()
        if (awayMs < gapMs) {
            aapsLogger.debug(LTag.NSCLIENT, "Back after ${awayMs}ms, short enough to keep the connection")
            return
        }
        aapsLogger.debug(LTag.NSCLIENT, "Back after ${awayMs}ms, asking for a fresh connection")
        onResumeAfterGap(awayMs)
    }

    private fun observe(name: String?, action: () -> Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = name,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
            usingBlock = { action() }
        )
        observers.add(observer as NSObject)
    }

    companion object {

        /**
         * Below this, a reconnect would cost more than it buys: the socket is almost certainly still
         * alive after a glance at another app, and replacing it throws away the catch-up high-water
         * mark for nothing. Long enough to cover switching apps, short enough that a real suspension
         * is always caught, because iOS suspends a backgrounded app within a few seconds.
         */
        const val DEFAULT_GAP_MS: Long = 60_000
    }
}
