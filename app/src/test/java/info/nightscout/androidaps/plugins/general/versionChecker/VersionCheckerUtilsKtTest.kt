package info.nightscout.androidaps.plugins.general.versionChecker

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionCheckerUtilsKtTest {
    @Test
    fun findVersionMatches() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "2.2.2"
            |   appName = "Aaoeu"
        """.trimMargin()
        val detectedVersion: String? = buildGradle.byteInputStream().findVersion()
        assertEquals("2.2.2", detectedVersion)
    }
}