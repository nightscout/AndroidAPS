package info.nightscout.androidaps.plugins.constraints.versionChecker

import android.content.Context
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

class VersionCheckerUtilsKtTest : TestBase() {

    lateinit var versionCheckerUtils: VersionCheckerUtils

    @Mock lateinit var sp: SP
    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var context: Context
    val config = Config()

    val rxBus = RxBusWrapper()

    @Before fun setup() {
        versionCheckerUtils = VersionCheckerUtils(aapsLogger, sp, resourceHelper, rxBus, config, context)
    }

    @Test
    fun `should handle invalid version`() {
        assertArrayEquals(intArrayOf(), versionCheckerUtils.versionDigits("definitely not version string"))
    }

    @Test
    fun `should handle empty version`() {
        assertArrayEquals(intArrayOf(), versionCheckerUtils.versionDigits(""))
    }

    @Test
    fun `should parse 2 digit version`() {
        assertArrayEquals(intArrayOf(0, 999), versionCheckerUtils.versionDigits("0.999-beta"))
    }

    @Test
    fun `should parse 3 digit version`() {
        assertArrayEquals(intArrayOf(6, 83, 93), versionCheckerUtils.versionDigits("6.83.93"))
    }

    @Test
    fun `should parse 4 digit version`() {
        assertArrayEquals(intArrayOf(42, 7, 13, 101), versionCheckerUtils.versionDigits("42.7.13.101"))
    }

    @Test
    fun `should parse 4 digit version with extra`() {
        assertArrayEquals(intArrayOf(1, 2, 3, 4), versionCheckerUtils.versionDigits("1.2.3.4-RC5"))
    }

    @Test
    fun `should parse version but only 4 digits are taken`() {
        assertArrayEquals(intArrayOf(67, 8, 31, 5), versionCheckerUtils.versionDigits("67.8.31.5.153.4.2"))
    }

    /*
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

    */
    @Test
    fun findVersionMatchesRegularVersion() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "2.2.2"
            |   appName = "Aaoeu"
        """.trimMargin()
        val detectedVersion: String? = versionCheckerUtils.findVersion(buildGradle)
        assertEquals("2.2.2", detectedVersion)
    }

    /* TODO finish this tests
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
        val detectedVersion: String? = versionCheckerUtils.findVersion(buildGradle)
        assertEquals(null, detectedVersion)
    }

    @Test
    fun findVersionMatchesDoesNotMatchErrorResponse() {
        val buildGradle = """<html><body>Balls! No build.gradle here. Move along</body><html>"""
        val detectedVersion: String? = versionCheckerUtils.findVersion(buildGradle)
        assertEquals(null, detectedVersion)
    }

    /*
        @Test
        fun testVersionStrip() {
            assertEquals("2.2.2", "2.2.2".versionStrip())
            assertEquals("2.2.2", "2.2.2-dev".versionStrip())
            assertEquals("2.2.2", "2.2.2dev".versionStrip())
            assertEquals("2.2.2", """"2.2.2"""".versionStrip())
        }
    */
    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `should find update1`() {
        prepareMainApp()

        versionCheckerUtils.compareWithCurrentVersion(newVersion = "2.2.3", currentVersion = "2.2.1")

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

        versionCheckerUtils.compareWithCurrentVersion(newVersion = "2.2.3", currentVersion = "2.2.1-dev")

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

        versionCheckerUtils.compareWithCurrentVersion(newVersion = "2.2.3", currentVersion = "2.1")

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

        versionCheckerUtils.compareWithCurrentVersion(newVersion = "2.2", currentVersion = "2.1.1")

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
        versionCheckerUtils.compareWithCurrentVersion(newVersion = "2.2.1", currentVersion = "2.2-dev")

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
        versionCheckerUtils.compareWithCurrentVersion(newVersion = "2.2.1", currentVersion = "2.2dev")

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
        versionCheckerUtils.compareWithCurrentVersion(newVersion = "2.5.0", currentVersion = "2.5.0.1")

        //verify(bus, times(0)).post(any())

        PowerMockito.verifyStatic(SP::class.java, times(1))
        SP.putLong(eq(R.string.key_last_time_this_version_detected), ArgumentMatchers.anyLong())
        PowerMockito.verifyNoMoreInteractions(SP::class.java)
    }

    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun `should not find update on personal version with same number`() {
        prepareMainApp()
        versionCheckerUtils.compareWithCurrentVersion(newVersion = "2.5.0", currentVersion = "2.5.0-myversion")

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
        versionCheckerUtils.compareWithCurrentVersion(versionCheckerUtils.findVersion(buildGradle), currentVersion = "2.2.2")

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
        versionCheckerUtils.compareWithCurrentVersion(versionCheckerUtils.findVersion(buildGradle), currentVersion = "2.2.2")

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
        versionCheckerUtils.compareWithCurrentVersion(versionCheckerUtils.findVersion(buildGradle), currentVersion = "2.2.2")

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
        versionCheckerUtils.compareWithCurrentVersion(versionCheckerUtils.findVersion(buildGradle), currentVersion = "3.0-RC04")

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
        versionCheckerUtils.compareWithCurrentVersion(versionCheckerUtils.findVersion(buildGradle), currentVersion = "3.0RC04")

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
        versionCheckerUtils.compareWithCurrentVersion(versionCheckerUtils.findVersion(buildGradle), currentVersion = "3.RC04")

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
        versionCheckerUtils.compareWithCurrentVersion(versionCheckerUtils.findVersion(buildGradle), currentVersion = "3.0.RC04")

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
        versionCheckerUtils.compareWithCurrentVersion(versionCheckerUtils.findVersion(buildGradle), currentVersion = "3.7.9")

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
        versionCheckerUtils.compareWithCurrentVersion(versionCheckerUtils.findVersion(buildGradle), currentVersion = "2.3")

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
        versionCheckerUtils.compareWithCurrentVersion(versionCheckerUtils.findVersion(buildGradle), currentVersion = "2.3-RC")

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
    */
}
