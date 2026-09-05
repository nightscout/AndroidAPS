package app.aaps.desktop.shell.config

import app.aaps.core.interfaces.configuration.ExternalOptions
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
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
}
