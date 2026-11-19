package app.aaps.ios.shell.config

import app.aaps.core.interfaces.configuration.ExternalOptions
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the build flags say, for the full app and for a client.
 *
 * [IosClientFlavorTest] checks the pure function that reads the bundle identifier. This one builds
 * the real config from an identifier and checks what shared code actually reads: whether the loop
 * runs, whether pump drivers are offered, whether this is a follower. It also guards two traps that
 * the pure test cannot see:
 *
 * - The flags are worked out from `FLAVOR`, and Kotlin sets properties in the order they are written.
 *   Moved above `FLAVOR`, every flag would read it before it is set and quietly be false - the full
 *   app would then start as something that is neither the full app nor a client.
 * - [IosClientConfig.isEngineeringModeOrRelease] is the gate `SafetyPlugin` uses to keep a
 *   development build from closing the loop. It was a fixed `true` while iOS built followers only.
 *
 * A made-up team id is used throughout: a real one does not belong in a public repository.
 */
@OptIn(ExperimentalForeignApi::class)
class IosFullBuildTest {

    private val manager = NSFileManager.defaultManager
    private val extra = NSTemporaryDirectory() + "aaps-full-" + NSUUID().UUIDString()

    init {
        manager.createDirectoryAtPath(extra, withIntermediateDirectories = true, attributes = null, error = null)
    }

    @AfterTest
    fun removeDirectory() {
        manager.removeItemAtPath(extra, null)
    }

    private fun config(bundleId: String) = IosClientConfig(APPLICATION_ID = bundleId, extraDir = extra)

    private fun createMarker(option: ExternalOptions) {
        manager.createFileAtPath("$extra/${option.filename}", contents = null, attributes = null)
    }

    @Test
    fun `the full app runs the loop and drives a pump`() {
        val full = config("app.aaps.ABCDE12345.full")

        assertTrue(full.APS, "APS")
        assertTrue(full.PUMPDRIVERS, "PUMPDRIVERS")
        assertFalse(full.AAPSCLIENT, "AAPSCLIENT")
        assertFalse(full.PUMPCONTROL, "PUMPCONTROL")
    }

    /** The full app is not a numbered client either, or the overview would tint it as one. */
    @Test
    fun `the full app is none of the three clients`() {
        val full = config("app.aaps.ABCDE12345.full")

        assertFalse(full.AAPSCLIENT1)
        assertFalse(full.AAPSCLIENT2)
        assertFalse(full.AAPSCLIENT3)
    }

    /** Both forms of a client - published, and built by somebody themselves - stay followers. */
    @Test
    fun `a client neither runs the loop nor drives a pump`() {
        listOf("app.aaps.client", "app.aaps.client2", "app.aaps.ABCDE12345.client", "app.aaps.ABCDE12345.client2").forEach { id ->
            val client = config(id)
            assertFalse(client.APS, "$id APS")
            assertFalse(client.PUMPDRIVERS, "$id PUMPDRIVERS")
            assertTrue(client.AAPSCLIENT, "$id AAPSCLIENT")
        }
    }

    /**
     * Exactly one of the two roles, for every identifier, including ones nothing builds. An identifier
     * that answered neither would start an app that is no kind of AAPS at all, and one that answered
     * both would be a follower running the loop.
     */
    @Test
    fun `every build is either the full app or a client and never both or neither`() {
        listOf(
            "app.aaps.ABCDE12345.full", "app.aaps.client", "app.aaps.client2", "app.aaps.ABCDE12345.client3",
            "info.nightscout.aapsclient", "app.aaps.awful", "com.apple.dt.xctest.tool", ""
        ).forEach { id ->
            val c = config(id)
            assertTrue(c.APS != c.AAPSCLIENT, "$id: APS=${c.APS} AAPSCLIENT=${c.AAPSCLIENT}")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The closed loop gate
    // ---------------------------------------------------------------------------------------------

    /**
     * A test binary has no `AAPSBuildType` in its bundle, so the config treats it as a development
     * build. The tests below depend on that, so it is checked rather than assumed: if it ever changed,
     * they would fail here with a reason instead of passing or failing for the wrong one.
     */
    private fun requireDevelopmentBuild(c: IosClientConfig) =
        assertTrue(c.isDev(), "the tests below assume a development build, and this one is not")

    @Test
    fun `a development build of the full app may not close the loop without engineering mode`() {
        val full = config("app.aaps.ABCDE12345.full")
        requireDevelopmentBuild(full)

        assertFalse(full.isEngineeringModeOrRelease())
    }

    @Test
    fun `engineering mode lets a development build of the full app close the loop`() {
        val full = config("app.aaps.ABCDE12345.full")
        requireDevelopmentBuild(full)

        createMarker(ExternalOptions.ENGINEERING_MODE)

        assertTrue(full.isEngineeringModeOrRelease())
    }

    /** A follower has no loop to close, so the gate never stops it, in any kind of build. */
    @Test
    fun `a client is never held back by the gate`() {
        val client = config("app.aaps.client")
        requireDevelopmentBuild(client)

        assertTrue(client.isEngineeringModeOrRelease())
    }
}
