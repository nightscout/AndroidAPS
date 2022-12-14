package info.nightscout.plugins.constraints.signatureVerifier

import dagger.Lazy
import info.nightscout.androidaps.TestBase
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.receivers.ReceiverStatusStore
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.interfaces.versionChecker.VersionCheckerUtils
import info.nightscout.plugins.constraints.versionChecker.VersionCheckerUtilsImpl
import info.nightscout.plugins.constraints.versionChecker.numericVersionPart
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.anyString
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@Suppress("SpellCheckingInspection") class VersionCheckerUtilsKtTest : TestBase() {

    private lateinit var versionCheckerUtils: VersionCheckerUtils

    @Mock lateinit var sp: SP
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock lateinit var config: Lazy<Config>
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var uiInteraction: UiInteraction

    @BeforeEach fun setup() {
        versionCheckerUtils = VersionCheckerUtilsImpl(aapsLogger, sp, rh, config, receiverStatusStore, dateUtil, uiInteraction)
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

    @Suppress("SpellCheckingInspection")
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

    @Test
    fun `should find update1`() {
        versionCheckerUtils.compareWithCurrentVersion(newVersion = "2.2.3", currentVersion = "2.2.1")
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `should find update2`() {
        versionCheckerUtils.compareWithCurrentVersion(newVersion = "2.2.3", currentVersion = "2.2.1-dev")
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `should find update3`() {
        versionCheckerUtils.compareWithCurrentVersion(newVersion = "2.2.3", currentVersion = "2.1")
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `should find update4`() {
        versionCheckerUtils.compareWithCurrentVersion(newVersion = "2.2", currentVersion = "2.1.1")
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `should find update5`() {
        versionCheckerUtils.compareWithCurrentVersion(newVersion = "2.2.1", currentVersion = "2.2-dev")
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `should find update6`() {
        versionCheckerUtils.compareWithCurrentVersion(newVersion = "2.2.1", currentVersion = "2.2dev")
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `should not find update on fourth version digit`() {
        versionCheckerUtils.compareWithCurrentVersion(newVersion = "2.5.0", currentVersion = "2.5.0.1")
        verify(uiInteraction, times(0)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `should not find update on personal version with same number`() {
        versionCheckerUtils.compareWithCurrentVersion(newVersion = "2.5.0", currentVersion = "2.5.0-myversion")
        verify(uiInteraction, times(0)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `find same version`() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "2.2.2"
            |   appName = "Aaoeu"
        """.trimMargin()
        versionCheckerUtils.compareWithCurrentVersion(versionCheckerUtils.findVersion(buildGradle), currentVersion = "2.2.2")

        verify(uiInteraction, times(0)).addNotification(anyInt(), anyString(), anyInt())
    }
    @Test
    fun `find higher version`() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "3.0.0"
            |   appName = "Aaoeu"
        """.trimMargin()
        versionCheckerUtils.compareWithCurrentVersion(versionCheckerUtils.findVersion(buildGradle), currentVersion = "2.2.2")
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `find higher version with longer number`() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "3.0"
            |   appName = "Aaoeu"
        """.trimMargin()
        versionCheckerUtils.compareWithCurrentVersion(versionCheckerUtils.findVersion(buildGradle), currentVersion = "2.2.2")
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `find higher version after RC`() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "3.0.0"
            |   appName = "Aaoeu"
        """.trimMargin()
        versionCheckerUtils.compareWithCurrentVersion(versionCheckerUtils.findVersion(buildGradle), currentVersion = "3.0-RC04")
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `find higher version after RC 2 - typo`() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "3.0.0"
            |   appName = "Aaoeu"
        """.trimMargin()
        versionCheckerUtils.compareWithCurrentVersion(versionCheckerUtils.findVersion(buildGradle), currentVersion = "3.0RC04")
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `find higher version after RC 3 - typo`() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "3.0.0"
            |   appName = "Aaoeu"
        """.trimMargin()
        versionCheckerUtils.compareWithCurrentVersion(versionCheckerUtils.findVersion(buildGradle), currentVersion = "3.RC04")
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `find higher version after RC 4 - typo`() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "3.0.0"
            |   appName = "Aaoeu"
        """.trimMargin()
        versionCheckerUtils.compareWithCurrentVersion(versionCheckerUtils.findVersion(buildGradle), currentVersion = "3.0.RC04")
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `find higher version on multi digit numbers`() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "3.7.12"
            |   appName = "Aaoeu"
        """.trimMargin()
        versionCheckerUtils.compareWithCurrentVersion(versionCheckerUtils.findVersion(buildGradle), currentVersion = "3.7.9")
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `don't find higher version on higher but shorter version`() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "2.2.2"
            |   appName = "Aaoeu"
        """.trimMargin()
        versionCheckerUtils.compareWithCurrentVersion(versionCheckerUtils.findVersion(buildGradle), currentVersion = "2.3")
        verify(uiInteraction, times(0)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `don't find higher version if on next RC`() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "2.2.2"
            |   appName = "Aaoeu"
        """.trimMargin()
        versionCheckerUtils.compareWithCurrentVersion(versionCheckerUtils.findVersion(buildGradle), currentVersion = "2.3-RC")
        verify(uiInteraction, times(0)).addNotification(anyInt(), anyString(), anyInt())
    }

   @Test
    fun `warn on beta`() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "2.2.2"
            |   appName = "Aaoeu"
        """.trimMargin()
        versionCheckerUtils.compareWithCurrentVersion(versionCheckerUtils.findVersion(buildGradle), currentVersion = "2.2-beta1")
       verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

   @Test
    fun `warn on rc`() {
        val buildGradle = """blabla
            |   android {
            |      aosenuthoae
            |   }
            |   version = "2.2.0"
            |   appName = "Aaoeu"
        """.trimMargin()
        versionCheckerUtils.compareWithCurrentVersion(versionCheckerUtils.findVersion(buildGradle), currentVersion = "2.2-rc1")
       verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @BeforeEach
    fun `set time`() {
        `when`(dateUtil.now()).thenReturn(10000000000L)
        assertEquals(10000000000L, dateUtil.now())

        `when`(rh.gs(anyInt(), anyString())).thenReturn("")
    }

}
