package app.aaps.ios.shell.ui

import app.aaps.core.interfaces.utils.fabric.CrashReporter
import app.aaps.core.utils.gitRemoteShort
import app.aaps.ios.shell.config.IosClientConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * What a crash report says about the build it came from.
 *
 * These keys are the whole reason a report from a fork can be told apart from one from upstream, so
 * the test is about the mapping being complete and honest, not about the values themselves - those
 * come from git at build time and differ on every machine.
 */
class BuildIdentityKeysTest {

    private class RecordingCrashReporter : CrashReporter {

        val keys = mutableMapOf<String, String>()
        override fun isAvailable(): Boolean = true
        override fun setCollectionEnabled(enabled: Boolean) = Unit
        override fun log(message: String) = Unit
        override fun setCustomKey(key: String, value: String) { keys[key] = value }
        override fun recordException(type: String, message: String, stackTrace: List<String>) = Unit
    }

    @Test
    fun `it sends the same six keys Android sends`() {
        val reporter = RecordingCrashReporter()

        setBuildIdentityKeys(reporter, IosClientConfig())

        assertEquals(
            setOf("HEAD", "Version", "BuildType", "BuildFlavor", "Remote", "Committed"),
            reporter.keys.keys.toSet()
        )
    }

    @Test
    fun `the commit is a real one and not the old placeholder`() {
        val reporter = RecordingCrashReporter()
        val config = IosClientConfig()

        setBuildIdentityKeys(reporter, config)

        // It was hardcoded "unknown" before the build started filling it in. A report with no commit
        // cannot be acted on, so this is the regression that matters most here.
        assertNotEquals("unknown", reporter.keys["HEAD"])
        assertEquals(config.HEAD, reporter.keys["HEAD"])
        assertTrue(reporter.keys["HEAD"]!!.isNotBlank())
    }

    @Test
    fun `the remote is sent in the short form like Android`() {
        val reporter = RecordingCrashReporter()
        val config = IosClientConfig()

        setBuildIdentityKeys(reporter, config)

        assertEquals(gitRemoteShort(config.REMOTE), reporter.keys["Remote"])
        // The long form would be the clone URL, so this guards against sending it raw.
        assertTrue(reporter.keys["Remote"]!!.startsWith("http").not())
    }

    @Test
    fun `whether the tree was clean is sent as a plain true or false`() {
        val reporter = RecordingCrashReporter()
        val config = IosClientConfig()

        setBuildIdentityKeys(reporter, config)

        assertEquals(config.COMMITTED.toString(), reporter.keys["Committed"])
        assertTrue(reporter.keys["Committed"] in setOf("true", "false"))
    }

    @Test
    fun `the version and build kind describe this build not a fixed one`() {
        val reporter = RecordingCrashReporter()
        val config = IosClientConfig()

        setBuildIdentityKeys(reporter, config)

        assertEquals(config.VERSION_NAME, reporter.keys["Version"])
        assertEquals(config.BUILD_TYPE, reporter.keys["BuildType"])
        assertEquals(config.FLAVOR, reporter.keys["BuildFlavor"])
        assertTrue(reporter.keys["Version"]!!.isNotBlank())
    }

    @Test
    fun `a reporter that was never configured is safe to call`() {
        // A clone with no GoogleService-Info.plist gets NoCrashReporter, and start up must not care.
        var calls = 0
        val silent = object : CrashReporter {
            override fun isAvailable(): Boolean = false
            override fun setCollectionEnabled(enabled: Boolean) = Unit
            override fun log(message: String) = Unit
            override fun setCustomKey(key: String, value: String) { calls++ }
            override fun recordException(type: String, message: String, stackTrace: List<String>) = Unit
        }

        setBuildIdentityKeys(silent, IosClientConfig())

        // The keys are still offered; it is the reporter's job to drop them, which is what keeps a
        // user who turns reporting on mid-session from getting unlabelled reports.
        assertEquals(6, calls)
    }
}
