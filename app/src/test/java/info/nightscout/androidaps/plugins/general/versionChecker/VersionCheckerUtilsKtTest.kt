package info.nightscout.androidaps.plugins.general.versionChecker

import com.squareup.otto.Bus
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.logging.L
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
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


    // In case we merge a "x.x.x-dev" into master, don't see it as update.
    @Test
    fun `should return null on non-digit versions on master`() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "2.2.2-nefarious-underground-mod"
            |   appName = "Aaoeu"
        """.trimMargin()
        val detectedVersion: String? = buildGradle.byteInputStream().findVersion()
        assertEquals(null, detectedVersion)
    }

    @Test
    fun findVersionMatchesDoesNotMatchErrorResponse() {
        val buildGradle = """<html><body>Balls! No build.gradle here. Move along</body><html>"""
        val detectedVersion: String? = buildGradle.byteInputStream().findVersion()
        assertEquals(null, detectedVersion)
    }

    @Test
    fun testVersionStrip() {
        assertEquals("2.2.2", "2.2.2".versionStrip())
        assertEquals("2.2.2", "2.2.2-dev".versionStrip())
        assertEquals("2.2.2", "2.2.2dev".versionStrip())
        assertEquals("2.2.2", """"2.2.2"""".versionStrip())
    }

    @Test
    @PrepareForTest(MainApp::class, L::class)
    fun `should find update1`() {
        val bus = prepareCompareTests()
        compareWithCurrentVersion(newVersion = "2.2.3", currentVersion = "2.2.1")
        verify(bus, times(1)).post(any())
    }

    @Test
    @PrepareForTest(MainApp::class, L::class)
    fun `should find update2`() {
        val bus = prepareCompareTests()
        compareWithCurrentVersion(newVersion = "2.2.3", currentVersion = "2.2.1-dev")
        verify(bus, times(1)).post(any())
    }

    @Test
    @PrepareForTest(MainApp::class, L::class)
    fun `should find update3`() {
        val bus = prepareCompareTests()
        compareWithCurrentVersion(newVersion = "2.2.3", currentVersion = "2.1")
        verify(bus, times(1)).post(any())
    }

    @Test
    @PrepareForTest(MainApp::class, L::class)
    fun `should find update4`() {
        val bus = prepareCompareTests()

        compareWithCurrentVersion(newVersion = "2.2", currentVersion = "2.1.1")

        verify(bus, times(1)).post(any())
    }

    @Test
    @PrepareForTest(MainApp::class, L::class)
    fun `should find update5`() {
        val bus = prepareCompareTests()
        compareWithCurrentVersion(newVersion = "2.2.1", currentVersion = "2.2-dev")
        verify(bus, times(1)).post(any())
    }

    @Test
    @PrepareForTest(MainApp::class, L::class)
    fun `should find update6`() {
        val bus = prepareCompareTests()
        compareWithCurrentVersion(newVersion = "2.2.1", currentVersion = "2.2dev")
        verify(bus, times(1)).post(any())
    }


    private fun prepareCompareTests(): Bus {
        PowerMockito.mockStatic(MainApp::class.java)
        val mainApp = mock<MainApp>(MainApp::class.java)
        `when`(MainApp.instance()).thenReturn(mainApp)
        val bus = mock(Bus::class.java)
        `when`(MainApp.bus()).thenReturn(bus)
        `when`(MainApp.gs(ArgumentMatchers.anyInt())).thenReturn("some dummy string")
        return bus
    }
}