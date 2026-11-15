package app.aaps.implementation.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The count decides whether a message becomes a system notification, so both directions matter: one
 * too many and a background message is silently dropped, one too few and a message on screen is
 * duplicated in the notification shade.
 */
class SnackbarHostPresenceImplTest {

    @Test
    fun startsWithNoHosts() {
        assertEquals(0, SnackbarHostPresenceImpl().activeHosts.value)
    }

    @Test
    fun countsEveryHostThatIsUp() {
        val presence = SnackbarHostPresenceImpl()

        presence.acquire()
        presence.acquire()

        assertEquals(2, presence.activeHosts.value)
    }

    @Test
    fun releasingOneLeavesTheOther() {
        val presence = SnackbarHostPresenceImpl()
        val first = presence.acquire()
        presence.acquire()

        first.close()

        assertEquals(1, presence.activeHosts.value)
    }

    @Test
    fun closingTheSameHandleTwiceReleasesOnce() {
        val presence = SnackbarHostPresenceImpl()
        val handle = presence.acquire()
        presence.acquire()

        handle.close()
        handle.close()

        assertEquals(1, presence.activeHosts.value)
    }
}
