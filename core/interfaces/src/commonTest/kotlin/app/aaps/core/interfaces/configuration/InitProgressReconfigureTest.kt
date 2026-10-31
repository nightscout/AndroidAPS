package app.aaps.core.interfaces.configuration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The reconfiguration window carried inside [InitProgress].
 *
 * The arithmetic lives here rather than in each `Config` implementation so there is one copy to get
 * right and one place to test it. Four implementations call [InitProgress.enteringReconfigure] and
 * [InitProgress.leavingReconfigure]; none of them do the counting themselves.
 */
class InitProgressReconfigureTest {

    @Test
    fun `a fresh progress is not reconfiguring`() {
        assertFalse(InitProgress().reconfiguring)
    }

    @Test
    fun `entering once marks it as reconfiguring`() {
        assertTrue(InitProgress(done = true).enteringReconfigure().reconfiguring)
    }

    /**
     * The whole reason [InitProgress.reconfiguringDepth] is a count and not a flag. An import opens a
     * window around the preference rewrite and another around the apply, and a nested caller must not
     * be able to close a window it did not open.
     */
    @Test
    fun `two entries need two exits`() {
        val twice = InitProgress(done = true).enteringReconfigure().enteringReconfigure()

        val afterOne = twice.leavingReconfigure()
        assertTrue(afterOne.reconfiguring, "one exit must not close a window opened twice")

        assertFalse(afterOne.leavingReconfigure().reconfiguring)
    }

    /**
     * An unbalanced exit must not drive the count negative. If it did, the next real entry would take
     * it from -1 to 0 and read as "not reconfiguring" - the window would silently stop working, which
     * is the failure that has no symptom.
     */
    @Test
    fun `leaving more often than entering never goes below zero`() {
        val over = InitProgress(done = true).leavingReconfigure().leavingReconfigure()
        assertEquals(0, over.reconfiguringDepth)

        assertTrue(over.enteringReconfigure().reconfiguring, "the next entry must still open the window")
    }

    /**
     * `done` is the splash gate. `AapsAppRoot` shows the splash under
     * `AnimatedVisibility(visible = !initProgress.done)` and the app content under the inverse, and
     * `AnimatedVisibility` removes the subtree from composition - so clearing `done` to express
     * "reconfiguring" would take the import screen off the display in the middle of its own apply.
     * That is why this state is a separate field.
     */
    @Test
    fun `reconfiguring never disturbs done`() {
        val reconfiguring = InitProgress(done = true).enteringReconfigure()

        assertTrue(reconfiguring.done)
        assertTrue(reconfiguring.leavingReconfigure().done)
    }

    @Test
    fun `entering and leaving keep the progress text and error untouched`() {
        val original = InitProgress(step = "Loading", current = 2, total = 7, done = true, error = "boom")

        val round = original.enteringReconfigure().leavingReconfigure()

        assertEquals(original, round)
    }
}
