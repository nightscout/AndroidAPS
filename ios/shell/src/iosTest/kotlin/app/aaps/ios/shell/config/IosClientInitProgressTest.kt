package app.aaps.ios.shell.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * That iOS starts with the splash screen up - the one thing holding the start together.
 *
 * `AppStartSequence` registers the plugins itself, in the background, after the preference migrations
 * and the database vacuum. Before it runs, `PluginStore.plugins` is an unset `lateinit`, and a view
 * model that reads the active pump then throws from its own constructor: on Kotlin/Native that
 * exception is unhandled and **aborts the process**. `AapsAppRoot` composes the app inside an
 * `AnimatedVisibility(visible = initProgress.done)`, which builds nothing while hidden, so reporting
 * "not done" here is what keeps any such view model from being built at all.
 *
 * This shell reported `done = true` from the start until the sequence was wired in, because it
 * registered the plugins itself before the first composition and had nothing for a splash to cover.
 * A change back to `true` would compile, show no splash, and crash on a slow start - which is why
 * this is a test and not a comment.
 */
class IosClientInitProgressTest {

    @Test
    fun `the app starts not done so the splash screen covers the start`() {
        val config = IosClientConfig(extraDir = null)

        assertFalse(config.initProgressFlow.value.done, "a fresh app must not claim to be ready")
    }

    @Test
    fun `initCompleted is what releases the app`() {
        val config = IosClientConfig(extraDir = null)
        config.initCompleted()

        assertTrue(config.initProgressFlow.value.done)
    }

    /**
     * The splash is where a failed start is shown: "Initialization failed", the reason and Close. It is
     * drawn only while not done, so a failure must leave `done` false.
     *
     * This test used to say the opposite - "done as well, or the failure screen would never be composed"
     * - and pinned `done = true`. That hid the reason and built the app's screens over a start that never
     * finished. On the first run of the full app it ended in "No pump selected" from a view model, which
     * on Kotlin/Native aborts the process. Android has always kept `done` false here.
     */
    @Test
    fun `a failed start keeps the splash up and carries the reason`() {
        val config = IosClientConfig(extraDir = null)
        config.updateInitProgress("Initializing plugins", current = 3, total = 7)
        config.initFailed("Default plugin not found")

        assertFalse(config.initProgressFlow.value.done, "the splash is where the failure is shown")
        assertEquals("Default plugin not found", config.initProgressFlow.value.error)
        assertFalse(config.appInitialized, "nothing may read the plugins after a failed start")
    }

    @Test
    fun `progress steps keep the app behind the splash`() {
        val config = IosClientConfig(extraDir = null)
        config.updateInitProgress("migrating", current = 2, total = 7)

        assertFalse(config.initProgressFlow.value.done, "a step is progress, not completion")
        assertEquals("migrating", config.initProgressFlow.value.step)
        assertEquals(2, config.initProgressFlow.value.current)
        assertEquals(7, config.initProgressFlow.value.total)
    }

    @Test
    fun `a reconfigure window survives a progress step`() {
        // The depth belongs to whoever opened the window, not to the start. Clobbering it would reopen
        // the app to readers in the middle of an import.
        val config = IosClientConfig(extraDir = null)
        config.initCompleted()
        config.beginReconfiguring()
        config.updateInitProgress("importing", current = 1, total = 3)

        assertEquals(1, config.initProgressFlow.value.reconfiguringDepth)

        config.endReconfiguring()
        assertEquals(0, config.initProgressFlow.value.reconfiguringDepth)
    }
}
