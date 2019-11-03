package info.nightscout.androidaps.plugins.constraints.versionChecker

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
    fun `should keep 2 digit version`() {
        assertEquals("1.2", "1.2".numericVersionPart())
    }

    @Test
    fun `should keep 3 digit version`() {
        assertEquals("1.2.3", "1.2.3".numericVersionPart())
    }

    @Test
    fun `should keep 4 digit version`() {
        assertEquals("1.2.3.4", "1.2.3.4".numericVersionPart())
    }

    @Test
    fun `should strip 2 digit version RC`() {
        assertEquals("1.2", "1.2-RC1".numericVersionPart())
    }

    @Test
    fun `should strip 2 digit version RC old format`() {
        assertEquals("1.2", "1.2RC1".numericVersionPart())
    }

    @Test
    fun `should strip 2 digit version RC without digit`() {
        assertEquals("1.2", "1.2-RC".numericVersionPart())
    }

    @Test
    fun `should strip 2 digit version dev`() {
        assertEquals("1.2", "1.2-dev".numericVersionPart())
    }

    @Test
    fun `should strip 2 digit version dev old format 1`() {
        assertEquals("1.2", "1.2dev".numericVersionPart())
    }

    @Test
    fun `should strip 2 digit version dev old format 2`() {
        assertEquals("1.2", "1.2dev-a3".numericVersionPart())
    }

    @Test
    fun `should strip 3 digit version RC`() {
        assertEquals("1.2.3", "1.2.3-RC1".numericVersionPart())
    }

    @Test
    fun `should strip 4 digit version RC`() {
        assertEquals("1.2.3.4", "1.2.3.4-RC5".numericVersionPart())
    }

    @Test
    fun `should strip even with dot`() {
        assertEquals("1.2", "1.2.RC5".numericVersionPart())
    }


    @Test
    fun findVersionMatchesRegularVersion() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "2.2.2"
            |   appName = "Aaoeu"
        """.trimMargin()
        val detectedVersion: String? = findVersion(buildGradle)
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
        val detectedVersion: String? = findVersion(buildGradle)
        assertEquals(null, detectedVersion)
    }

    @Test
    fun findVersionMatchesDoesNotMatchErrorResponse() {
        val buildGradle = """<html><body>Balls! No build.gradle here. Move along</body><html>"""
        val detectedVersion: String? = findVersion(buildGradle)
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
        prepareMainApp()

        compareWithCurrentVersion(newVersion = "2.2.3", currentVersion = "2.2.1")

        //verify(bus, times(1)).post(any())

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.getLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)

    }

    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `should find update2`() {
        prepareMainApp()

        compareWithCurrentVersion(newVersion = "2.2.3", currentVersion = "2.2.1-dev")

        //verify(bus, times(1)).post(any())

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.getLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)
    }

    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `should find update3`() {
        prepareMainApp()

        compareWithCurrentVersion(newVersion = "2.2.3", currentVersion = "2.1")

        //verify(bus, times(1)).post(any())

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.getLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)
    }

    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `should find update4`() {
        prepareMainApp()

        compareWithCurrentVersion(newVersion = "2.2", currentVersion = "2.1.1")

        //verify(bus, times(1)).post(any())

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.getLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)
    }

    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `should find update5`() {
        prepareMainApp()
        compareWithCurrentVersion(newVersion = "2.2.1", currentVersion = "2.2-dev")

        //verify(bus, times(1)).post(any())

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.getLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)
    }

    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `should find update6`() {
        prepareMainApp()
        compareWithCurrentVersion(newVersion = "2.2.1", currentVersion = "2.2dev")

        //verify(bus, times(1)).post(any())

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.getLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)
    }

    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `should not find update on fourth version digit`() {
        prepareMainApp()
        compareWithCurrentVersion(newVersion = "2.5.0", currentVersion = "2.5.0.1")

        //verify(bus, times(0)).post(any())

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_time_this_version_detected), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)
    }

    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `should not find update on personal version with same number` (){
        prepareMainApp()
        compareWithCurrentVersion(newVersion = "2.5.0", currentVersion = "2.5.0-myversion")

        //verify(bus, times(0)).post(any())

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_time_this_version_detected), ArgumentMatchers.anyLong())
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
        prepareMainApp()
        compareWithCurrentVersion(findVersion(buildGradle), currentVersion = "2.2.2")

        //verify(bus, times(0)).post(any())

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
            |   version = "3.0.0"
            |   appName = "Aaoeu"
        """.trimMargin()
        prepareMainApp()
        compareWithCurrentVersion(findVersion(buildGradle), currentVersion = "2.2.2")

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.getLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)
    }


    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `find higher version with longer number`() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "3.0"
            |   appName = "Aaoeu"
        """.trimMargin()
        prepareMainApp()
        compareWithCurrentVersion(findVersion(buildGradle), currentVersion = "2.2.2")

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.getLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)
    }

    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `find higher version after RC`() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "3.0.0"
            |   appName = "Aaoeu"
        """.trimMargin()
        prepareMainApp()
        compareWithCurrentVersion(findVersion(buildGradle), currentVersion = "3.0-RC04")

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.getLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)
    }

    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `find higher version after RC 2 - typo`() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "3.0.0"
            |   appName = "Aaoeu"
        """.trimMargin()
        prepareMainApp()
        compareWithCurrentVersion(findVersion(buildGradle), currentVersion = "3.0RC04")

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.getLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)
    }

    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `find higher version after RC 3 - typo`() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "3.0.0"
            |   appName = "Aaoeu"
        """.trimMargin()
        prepareMainApp()
        compareWithCurrentVersion(findVersion(buildGradle), currentVersion = "3.RC04")

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.getLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)
    }

    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `find higher version after RC 4 - typo`() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "3.0.0"
            |   appName = "Aaoeu"
        """.trimMargin()
        prepareMainApp()
        compareWithCurrentVersion(findVersion(buildGradle), currentVersion = "3.0.RC04")

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.getLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)
    }

    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `find higher version on multi digit numbers`() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "3.7.12"
            |   appName = "Aaoeu"
        """.trimMargin()
        prepareMainApp()
        compareWithCurrentVersion(findVersion(buildGradle), currentVersion = "3.7.9")

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.getLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_versionchecker_warning), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)
    }

    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `don't find higher version on higher but shorter version`() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "2.2.2"
            |   appName = "Aaoeu"
        """.trimMargin()
        prepareMainApp()
        compareWithCurrentVersion(findVersion(buildGradle), currentVersion = "2.3")

        //verify(bus, times(0)).post(any())

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_time_this_version_detected), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)
    }

    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `don't find higher version if on next RC`() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "2.2.2"
            |   appName = "Aaoeu"
        """.trimMargin()
        prepareMainApp()
        compareWithCurrentVersion(findVersion(buildGradle), currentVersion = "2.3-RC")

        //verify(bus, times(0)).post(any())

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_time_this_version_detected), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)
    }




    @Test
    @PrepareForTest(System::class)
    fun `set time`() {
        PowerMockito.spy(System::class.java)
        PowerMockito.`when`(System.currentTimeMillis()).thenReturn(100L)

        assertEquals(100L, System.currentTimeMillis())
    }

    private fun prepareMainApp() {
        PowerMockito.mockStatic(MainApp::class.java)
        val mainApp = mock<MainApp>(MainApp::class.java)
        `when`(MainApp.instance()).thenReturn(mainApp)
        `when`(MainApp.gs(ArgumentMatchers.anyInt())).thenReturn("some dummy string")
        prepareSP()
    }

    private fun prepareSP() {
        PowerMockito.mockStatic(SP::class.java)
    }

    private fun prepareLogging() {
        PowerMockito.mockStatic(L::class.java)
        `when`(L.isEnabled(any())).thenReturn(true)
    }

}
