package app.aaps.core.interfaces.notifications

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound

/**
 * Routing between the handlers that share iOS's one delegate slot.
 *
 * The reason this class exists is that `setDelegate` replaces silently: two owners means the second
 * wins and the first one's callbacks stop, with no error anywhere. So what is tested here is that
 * every registered handler is reachable, and that one claiming a response stops the others seeing it.
 *
 * `dispatch` is exercised directly rather than through a real `UNNotificationResponse`, which cannot
 * be constructed outside a delivered notification.
 */
class IosNotificationDelegateTest {

    @BeforeTest
    fun clear() = IosNotificationDelegate.reset()

    @AfterTest
    fun clearAfter() = IosNotificationDelegate.reset()

    @Test
    fun `with no handlers nothing claims a response`() {
        assertFalse(IosNotificationDelegate.dispatch("action", "notification"))
    }

    @Test
    fun `a handler that claims a response reports it handled`() {
        IosNotificationDelegate.register(emptySet()) { _, _ -> true }

        assertTrue(IosNotificationDelegate.dispatch("action", "notification"))
    }

    /** A handler for another notification's actions must not swallow this one. */
    @Test
    fun `a handler that declines lets the next one see it`() {
        val seen = mutableListOf<String>()
        IosNotificationDelegate.register(emptySet()) { _, _ -> seen += "first"; false }
        IosNotificationDelegate.register(emptySet()) { _, _ -> seen += "second"; true }

        assertTrue(IosNotificationDelegate.dispatch("action", "notification"))
        assertEquals(listOf("first", "second"), seen)
    }

    /** Once claimed, later handlers are not consulted - that is what keeps ordering meaningful. */
    @Test
    fun `a claimed response stops at the handler that took it`() {
        val seen = mutableListOf<String>()
        IosNotificationDelegate.register(emptySet()) { _, _ -> seen += "first"; true }
        IosNotificationDelegate.register(emptySet()) { _, _ -> seen += "second"; true }

        IosNotificationDelegate.dispatch("action", "notification")

        assertEquals(listOf("first"), seen)
    }

    // ---------------------------------------------------------------------------------------------
    // Foreground presentation
    // ---------------------------------------------------------------------------------------------

    /**
     * Each flag is checked on its own rather than against one combined number, so a failure names
     * which part of the presentation was lost. Answering `willPresent` at all is what stops iOS
     * showing nothing while AAPS is frontmost; these three are what it should show.
     */
    @Test
    fun `a notification arriving while the app is in front is shown and listed and keeps its sound`() {
        val options = IosNotificationDelegate.PRESENTATION_OPTIONS

        assertEquals(
            UNNotificationPresentationOptionBanner,
            options and UNNotificationPresentationOptionBanner,
            "no banner while the app is in front"
        )
        assertEquals(
            UNNotificationPresentationOptionList,
            options and UNNotificationPresentationOptionList,
            "not kept in Notification Centre"
        )
        assertEquals(
            UNNotificationPresentationOptionSound,
            options and UNNotificationPresentationOptionSound,
            "the notification's own sound is suppressed"
        )
    }

    /** Both real users register: the notification platform and the loop notifier. */
    @Test
    fun `handlers from different modules coexist`() {
        var platformSaw = false
        var notifierSaw = false
        IosNotificationDelegate.register(emptySet()) { _, _ -> platformSaw = true; false }
        IosNotificationDelegate.register(emptySet()) { _, _ -> notifierSaw = true; false }

        IosNotificationDelegate.dispatch("action", "notification")

        assertTrue(platformSaw)
        assertTrue(notifierSaw)
    }
}
