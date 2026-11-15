package app.aaps.desktop.shell.config

import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.keys.interfaces.TextRef
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * That an option turned on by hand is actually read.
 *
 * The whole mechanism is a file existing, which makes both failure modes silent. `isEngineeringMode`
 * used to return a flat `false` and never consult [DesktopClientConfig.isEnabled] at all, and
 * `isEnabled` used to look in `~/.aaps` while Android reads `AAPS/extra` - so the file could be
 * created exactly as documented and nothing would happen. An option that is switched off and an
 * option that is never found look identical from the outside.
 */
class DesktopClientConfigOptionsTest {

    private val extraDir: File = Files.createTempDirectory("aaps-extra-test").toFile()
    private val config = DesktopClientConfig(extraDir = extraDir)

    @AfterTest
    fun cleanUp() {
        extraDir.deleteRecursively()
    }

    private fun createMarker(option: ExternalOptions) {
        File(extraDir, option.filename).writeText("")
    }

    @Test
    fun `an option with no marker file is off`() {
        assertFalse(config.isEnabled(ExternalOptions.ENGINEERING_MODE))
        assertFalse(config.isEnabled(ExternalOptions.UNFINISHED_MODE))
    }

    @Test
    fun `creating the marker turns the option on`() {
        createMarker(ExternalOptions.UNFINISHED_MODE)

        assertTrue(config.isEnabled(ExternalOptions.UNFINISHED_MODE))
        assertFalse(config.isEnabled(ExternalOptions.ENGINEERING_MODE), "only the one that was created")
    }

    /** The regression: this ignored the file entirely and always said no. */
    @Test
    fun `engineering mode follows its marker file`() {
        assertFalse(config.isEngineeringMode())

        createMarker(ExternalOptions.ENGINEERING_MODE)

        assertTrue(config.isEngineeringMode(), "engineering_mode in the extra folder should turn it on")
    }

    /**
     * True whatever the marker says, and that is Android's answer too.
     *
     * `ConfigImpl` computes `if (!APS) true else …`, and desktop is a follower with `APS = false`,
     * so the first branch always wins. Asserted both ways so a later change to `isEngineeringMode`
     * cannot quietly start gating features the client is supposed to show.
     */
    @Test
    fun `engineering mode or release is true for a follower either way`() {
        assertTrue(config.isEngineeringModeOrRelease())

        createMarker(ExternalOptions.ENGINEERING_MODE)

        assertTrue(config.isEngineeringModeOrRelease())
    }

    /**
     * Each client build says which one it is, and only one flag is ever set.
     *
     * The flags drive the app name and the window icon, and `-Pclient=N` is the only thing that
     * moves them. Two set at once, or none, would give a client the wrong identity on a Nightscout
     * site shared with the phone it follows.
     */
    @Test
    fun `exactly one client flag is set, whichever client this build is`() {
        (1..3).forEach { n ->
            val built = DesktopClientConfig(client = n, extraDir = extraDir)

            assertEquals(
                1,
                listOf(built.AAPSCLIENT1, built.AAPSCLIENT2, built.AAPSCLIENT3).count { it },
                "client $n should set exactly one flag"
            )
            assertTrue(built.AAPSCLIENT, "every desktop build is a client")
        }
    }

    /** The ids the Android clients use, so two clients on one site are told apart. */
    @Test
    fun `each client has its own application id`() {
        val ids = (1..3).map { DesktopClientConfig(client = it, extraDir = extraDir).APPLICATION_ID }

        assertEquals(3, ids.toSet().size, "two clients must not claim the same application id")
        assertEquals("info.nightscout.aapsclient", ids.first(), "client 1 keeps the id it already had")
    }

    /** The name follows the flags, so a window and a package do not disagree. */
    @Test
    fun `the app name follows the client`() {
        val names = (1..3).map { n ->
            val built = DesktopClientConfig(client = n, extraDir = extraDir)
            (built.appName as TextRef.Literal).text
        }

        assertEquals(listOf("AAPSClient", "AAPSClient2", "AAPSClient3"), names)
    }

    /**
     * Init progress must not close a reconfiguration window it does not own.
     *
     * These three replace the whole [app.aaps.core.interfaces.configuration.InitProgress] rather than
     * copying it, so the depth has to be carried across by hand. Getting it wrong reopens the app to
     * readers while a settings import still believes plugin state is being rebuilt - and this shape is
     * shared with `IosClientConfig`, where it is worse: iOS starts at `done = true` and has no start-up
     * sequence that would put the state right afterwards.
     */
    @Test
    fun `init progress does not clear an open reconfiguration window`() {
        config.beginReconfiguring()
        assertFalse(config.appInitialized)

        config.updateInitProgress("Loading", 1, 3)
        assertTrue(config.initProgressFlow.value.reconfiguring, "progress must not close the window")

        config.initCompleted()
        assertTrue(config.initProgressFlow.value.reconfiguring, "init completing must not close the window")

        config.initFailed("boom")
        assertTrue(config.initProgressFlow.value.reconfiguring, "init failing must not close the window")

        // Only the owner closes it, and then the app is available again.
        config.endReconfiguring()
        assertFalse(config.initProgressFlow.value.reconfiguring)
        assertTrue(config.appInitialized)
    }
}
