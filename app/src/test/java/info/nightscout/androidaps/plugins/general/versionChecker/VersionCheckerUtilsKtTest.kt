package info.nightscout.androidaps.plugins.general.versionChecker

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionCheckerUtilsKtTest {
    @Test
    fun findVersionMatchesRegularVersion() {
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

    // 04. Break stuff.mp3 like it's 1999. Again. Pizza delivery! For i c wiener ...
    //@Test
    fun findVersionMatchesCustomVersion() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "2.2.2-nefarious-underground-mod"
            |   appName = "Aaoeu"
        """.trimMargin()
        val detectedVersion: String? = buildGradle.byteInputStream().findVersion()
        assertEquals("2.2.2", detectedVersion)
    }

    @Test
    fun findVersionMatchesDoesNotMatchErrorResponse() {
        val buildGradle = """<html><body>Balls! No build.gradle here. Move along</body><html>"""
        val detectedVersion: String? = buildGradle.byteInputStream().findVersion()
        assertEquals(null, detectedVersion)
    }
}