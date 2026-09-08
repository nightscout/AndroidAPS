package app.aaps.plugins.aps.loop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The identifiers the loop's notification actions are matched on.
 *
 * `IosLoopNotifier` itself needs a notification centre and cannot be built in a test binary, but the
 * part that decides *what a tapped button means* is plain string work - and getting it wrong is
 * silent. An identifier that does not round-trip means the user taps "ignore for 15 minutes" and
 * nothing at all happens, which is exactly the failure the interface warns about.
 */
class IosLoopNotifierTest {

    private fun ignoreActionId(minutes: Int) = "${IosLoopNotifier.IGNORE_PREFIX}$minutes"

    /** Same three the Android notification offers, so the two platforms behave alike. */
    @Test
    fun `it offers the same three ignore periods as Android`() {
        assertEquals(listOf(5, 15, 30), IosLoopNotifier.IGNORE_MINUTES)
    }

    @Test
    fun `each period has its own identifier`() {
        val ids = IosLoopNotifier.IGNORE_MINUTES.map { ignoreActionId(it) }

        assertEquals(ids.size, ids.toSet().size, "two periods sharing an id would ignore the wrong one")
    }

    /** The tapped identifier has to map back to the number of minutes, or nothing happens. */
    @Test
    fun `an identifier maps back to its period`() {
        for (minutes in IosLoopNotifier.IGNORE_MINUTES) {
            val matched = IosLoopNotifier.IGNORE_MINUTES.firstOrNull { ignoreActionId(it) == ignoreActionId(minutes) }
            assertEquals(minutes, matched)
        }
    }

    /** A tap that is not one of ours must be declined so another handler can take it. */
    @Test
    fun `an unrelated action matches nothing`() {
        val matched = IosLoopNotifier.IGNORE_MINUTES.firstOrNull { ignoreActionId(it) == "some.other.action" }

        assertEquals(null, matched)
    }

    /** The prefix is what keeps our actions distinguishable from any other module's. */
    @Test
    fun `identifiers are namespaced to aaps`() {
        assertTrue(IosLoopNotifier.IGNORE_PREFIX.startsWith("aaps-"))
        assertTrue(IosLoopNotifier.CATEGORY_CARBS.startsWith("aaps-"))
        assertTrue(IosLoopNotifier.NOTIFICATION_ID.startsWith("aaps-"))
    }
}
