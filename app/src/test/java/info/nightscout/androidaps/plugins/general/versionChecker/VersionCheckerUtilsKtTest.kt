package info.nightscout.androidaps.plugins.general.versionChecker

import com.squareup.otto.Bus
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.utils.SP
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
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `should find update1`() {
        val bus = prepareBus()

        compareWithCurrentVersion(newVersion = "2.2.3", currentVersion = "2.2.1")

        verify(bus, times(1)).post(any())

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.getLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)

    }

    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `should find update2`() {
        val bus = prepareBus()

        compareWithCurrentVersion(newVersion = "2.2.3", currentVersion = "2.2.1-dev")

        verify(bus, times(1)).post(any())

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.getLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)
    }

    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `should find update3`() {
        val bus = prepareBus()

        compareWithCurrentVersion(newVersion = "2.2.3", currentVersion = "2.1")

        verify(bus, times(1)).post(any())

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.getLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)
    }

    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `should find update4`() {
        val bus = prepareBus()

        compareWithCurrentVersion(newVersion = "2.2", currentVersion = "2.1.1")

        verify(bus, times(1)).post(any())

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.getLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)
    }

    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `should find update5`() {
        val bus = prepareBus()
        compareWithCurrentVersion(newVersion = "2.2.1", currentVersion = "2.2-dev")

        verify(bus, times(1)).post(any())

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.getLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)
    }

    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `should find update6`() {
        val bus = prepareBus()
        compareWithCurrentVersion(newVersion = "2.2.1", currentVersion = "2.2dev")

        verify(bus, times(1)).post(any())

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.getLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)
    }

    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `find same version`() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "2.2.2"
            |   appName = "Aaoeu"
        """.trimMargin()
        val bus = prepareBus()
        compareWithCurrentVersion(buildGradle.byteInputStream().findVersion(), currentVersion = "2.2.2")

        verify(bus, times(0)).post(any())

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_time_this_version_detected), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)
    }

    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `find higher version`() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "3.0"
            |   appName = "Aaoeu"
        """.trimMargin()
        val bus = prepareBus()
        compareWithCurrentVersion(buildGradle.byteInputStream().findVersion(), currentVersion = "2.2.2")

        verify(bus, times(1)).post(any())

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.getLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)
    }


    @Test
    @PrepareForTest(System::class)
    fun `set time`() {
        PowerMockito.spy(System::class.java)
        PowerMockito.`when`(System.currentTimeMillis()).thenReturn(100L)

        assertEquals(100L, System.currentTimeMillis())
    }

    private fun prepareBus(): Bus {
        PowerMockito.mockStatic(MainApp::class.java)
        val mainApp = mock<MainApp>(MainApp::class.java)
        `when`(MainApp.instance()).thenReturn(mainApp)
        val bus = mock(Bus::class.java)
        `when`(MainApp.bus()).thenReturn(bus)
        `when`(MainApp.gs(ArgumentMatchers.anyInt())).thenReturn("some dummy string")
        prepareSP()
        return bus
    }

    private fun prepareSP() {
        PowerMockito.mockStatic(SP::class.java)
    }

    private fun prepareLogging() {
        PowerMockito.mockStatic(L::class.java)
        `when`(L.isEnabled(any())).thenReturn(true)
    }

}