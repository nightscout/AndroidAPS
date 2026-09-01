package app.aaps.core.interfaces.notifications

import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/**
 * The single owner of the notification centre's delegate.
 *
 * In `:core:interfaces` rather than `:implementation` because more than one module needs it - the
 * notification platform and the loop notifier live in different modules and must share the one
 * delegate slot iOS provides.
 *
 * iOS has exactly one delegate slot for the whole app, and `setDelegate` silently replaces whatever
 * was there. Two classes each setting their own is therefore not a conflict the compiler or the
 * runtime reports - the second one wins and the first one's callbacks simply stop arriving. That is
 * why this exists: the notification platform wants swipe-away dismissals, the loop notifier wants its
 * "ignore for N minutes" buttons, and both have to be served from one delegate.
 *
 * Categories work the same way. `setNotificationCategories` replaces the whole set, so they are
 * accumulated here and registered together rather than each caller overwriting the last.
 *
 * The delegate object is held in a property because the centre keeps only a weak reference to it -
 * a locally created delegate is collected and the callbacks stop, which looks exactly like iOS
 * simply not calling back.
 */
object IosNotificationDelegate {

    private val handlers = mutableListOf<(actionId: String, notificationId: String) -> Boolean>()
    private val categories = mutableSetOf<UNNotificationCategory>()

    /** Strong reference: the centre holds its delegate weakly. */
    private var delegate: RoutingDelegate? = null

    /**
     * Adds a handler and the categories it needs.
     *
     * [handler] returns true when it has dealt with the response, so ordering between handlers stays
     * explicit rather than every handler seeing every response.
     */
    fun register(
        categories: Set<UNNotificationCategory>,
        handler: (actionId: String, notificationId: String) -> Boolean
    ) {
        this.categories += categories
        handlers += handler
        // Only reaches the notification centre when there is something to register. A test that only
        // exercises routing passes no categories and therefore never touches UIKit, which cannot be
        // reached from a test binary anyway.
        if (categories.isNotEmpty()) install()
    }

    /**
     * Offers the response to each handler until one claims it.
     *
     * Takes the two identifiers rather than the `UNNotificationResponse` they came from: that is all
     * any handler needs, and a response cannot be constructed outside a delivered notification, so
     * passing it would make the routing untestable.
     */
    internal fun dispatch(actionId: String, notificationId: String): Boolean =
        handlers.any { it(actionId, notificationId) }

    /** Visible for tests, which must not inherit another test's handlers. */
    internal fun reset() {
        handlers.clear()
        categories.clear()
        delegate = null
    }

    private fun install() {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.setNotificationCategories(categories)
        if (delegate == null) {
            delegate = RoutingDelegate()
            center.setDelegate(delegate)
        }
    }

    private class RoutingDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {

        override fun userNotificationCenter(
            center: UNUserNotificationCenter,
            didReceiveNotificationResponse: UNNotificationResponse,
            withCompletionHandler: () -> Unit
        ) {
            dispatch(
                actionId = didReceiveNotificationResponse.actionIdentifier,
                notificationId = didReceiveNotificationResponse.notification.request.identifier
            )
            // Always called, handled or not: iOS holds the response open until it is.
            withCompletionHandler()
        }
    }
}
