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

    /** Whether [install] has run. Until it has, registering only records. */
    private var installed = false

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
        // Recorded only. Attaching to the notification centre is [install], which the app calls once
        // at start up - see there for why registering must not do it.
        if (installed) install()
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
        installed = false
    }

    /**
     * Attaches to the real notification centre: publishes the categories and claims the delegate slot.
     *
     * Called once from app start up, and deliberately **not** from [register].
     *
     * `UNUserNotificationCenter.currentNotificationCenter()` needs a real app bundle and throws
     * `bundleProxyForCurrentProcess is nil` without one. Registering used to attach immediately, which
     * meant simply *building the object graph* touched UIKit - `CommonNotificationManager` calls
     * `onDismissed` while it is being constructed, and that registers a category. The whole graph
     * therefore could not be created in a test binary, which is how this was found: a test of history
     * window scoping died on a notification API it never meant to use.
     *
     * Separating the two is also the better shape. Declaring what a category is, and claiming a
     * process-wide delegate slot, are different acts and only one of them needs an app.
     *
     * Registrations that arrive after this has run attach straight away, so ordering does not matter.
     */
    fun install() {
        installed = true
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
