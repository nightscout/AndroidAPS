package app.aaps.plugins.constraints.signatureVerifier

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.plugins.constraints.versionChecker.VersionCheckerUtilsImpl
import app.aaps.plugins.constraints.versionChecker.numericVersionPart
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.anyString
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@Suppress("SpellCheckingInspection")
class VersionCheckerUtilsKtTest : TestBase() {

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
        assertThat(versionCheckerUtils.versionDigits("definitely not version string")).isEmpty()
    }

    @Test
    fun `should handle empty version`() {
        assertThat(versionCheckerUtils.versionDigits("")).isEmpty()
    }

    @Test
    fun `should parse 2 digit version`() {
        assertThat(versionCheckerUtils.versionDigits("0.999-beta")).asList().containsExactly(0, 999).inOrder()
    }

    @Test
    fun `should parse 3 digit version`() {
        assertThat(versionCheckerUtils.versionDigits("6.83.93")).asList().containsExactly(6, 83, 93).inOrder()
    }

    @Test
    fun `should parse 4 digit version`() {
        assertThat(versionCheckerUtils.versionDigits("42.7.13.101")).asList().containsExactly(42, 7, 13, 101).inOrder()
    }

    @Test
    fun `should parse 4 digit version with extra`() {
        assertThat(versionCheckerUtils.versionDigits("1.2.3.4-RC5")).asList().containsExactly(1, 2, 3, 4).inOrder()
    }

    @Test
    fun `should parse version but only 4 digits are taken`() {
        assertThat(versionCheckerUtils.versionDigits("67.8.31.5.153.4.2")).asList().containsExactly(67, 8, 31, 5).inOrder()
    }

    @Test
    fun `should keep 2 digit version`() {
        assertThat("1.2".numericVersionPart()).isEqualTo("1.2")
    }

    @Test
    fun `should keep 3 digit version`() {
        assertThat("1.2.3".numericVersionPart()).isEqualTo("1.2.3")
    }

    @Test
    fun `should keep 4 digit version`() {
        assertThat("1.2.3.4".numericVersionPart()).isEqualTo("1.2.3.4")
    }

    @Test
    fun `should strip 2 digit version RC`() {
        assertThat("1.2-RC1".numericVersionPart()).isEqualTo("1.2")
    }

    @Test
    fun `should strip 2 digit version RC old format`() {
        assertThat("1.2RC1".numericVersionPart()).isEqualTo("1.2")
    }

    @Test
    fun `should strip 2 digit version RC without digit`() {
        assertThat("1.2-RC".numericVersionPart()).isEqualTo("1.2")
    }

    @Test
    fun `should strip 2 digit version dev`() {
        assertThat("1.2-dev".numericVersionPart()).isEqualTo("1.2")
    }

    @Test
    fun `should strip 2 digit version dev old format 1`() {
        assertThat("1.2dev".numericVersionPart()).isEqualTo("1.2")
    }

    @Test
    fun `should strip 2 digit version dev old format 2`() {
        assertThat("1.2dev-a3".numericVersionPart()).isEqualTo("1.2")
    }

    @Test
    fun `should strip 3 digit version RC`() {
        assertThat("1.2.3-RC1".numericVersionPart()).isEqualTo("1.2.3")
    }

    @Test
    fun `should strip 4 digit version RC`() {
        assertThat("1.2.3.4-RC5".numericVersionPart()).isEqualTo("1.2.3.4")
    }

    @Test
    fun `should strip even with dot`() {
        assertThat("1.2.RC5".numericVersionPart()).isEqualTo("1.2")
    }

    @Test
    fun `should find update1`() {
        Assertions.assertTrue(versionCheckerUtils.compareWithCurrentVersion(newVersion = "2.2.3", currentVersion = "2.2.1"))
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `should find update2`() {
        Assertions.assertTrue(versionCheckerUtils.compareWithCurrentVersion(newVersion = "2.2.3", currentVersion = "2.2.1-dev"))
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `should find update3`() {
        Assertions.assertTrue(versionCheckerUtils.compareWithCurrentVersion(newVersion = "2.2.3", currentVersion = "2.1"))
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `should find update4`() {
        Assertions.assertTrue(versionCheckerUtils.compareWithCurrentVersion(newVersion = "2.2", currentVersion = "2.1.1"))
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `should find update5`() {
        Assertions.assertTrue(versionCheckerUtils.compareWithCurrentVersion(newVersion = "2.2.1", currentVersion = "2.2-dev"))
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `should find update6`() {
        Assertions.assertTrue(versionCheckerUtils.compareWithCurrentVersion(newVersion = "2.2.1", currentVersion = "2.2dev"))
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `should not find update on fourth version digit`() {
        Assertions.assertFalse(versionCheckerUtils.compareWithCurrentVersion(newVersion = "2.5.0", currentVersion = "2.5.0.1"))
        verify(uiInteraction, times(0)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `should not find update on personal version with same number`() {
        Assertions.assertFalse(versionCheckerUtils.compareWithCurrentVersion(newVersion = "2.5.0", currentVersion = "2.5.0-myversion"))
        verify(uiInteraction, times(0)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `find same version`() {
        Assertions.assertFalse(versionCheckerUtils.compareWithCurrentVersion("2.2.2", currentVersion = "2.2.2"))
        verify(uiInteraction, times(0)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `find higher version`() {
        Assertions.assertTrue(versionCheckerUtils.compareWithCurrentVersion("3.0.0", currentVersion = "2.2.2"))
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `find higher version with longer number`() {
        Assertions.assertTrue(versionCheckerUtils.compareWithCurrentVersion("3.0", currentVersion = "2.2.2"))
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `find higher version after RC`() {
        Assertions.assertTrue(versionCheckerUtils.compareWithCurrentVersion("3.0.0", currentVersion = "3.0-RC04"))
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `find higher version after RC 2 - typo`() {
        Assertions.assertTrue(versionCheckerUtils.compareWithCurrentVersion("3.0.0", currentVersion = "3.0RC04"))
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `find higher version after RC 3 - typo`() {
        Assertions.assertTrue(versionCheckerUtils.compareWithCurrentVersion("3.0.0", currentVersion = "3.RC04"))
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `find higher version after RC 4 - typo`() {
        Assertions.assertTrue(versionCheckerUtils.compareWithCurrentVersion("3.0.0", currentVersion = "3.0.RC04"))
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `find higher version on multi digit numbers`() {
        Assertions.assertTrue(versionCheckerUtils.compareWithCurrentVersion("3.7.12", currentVersion = "3.7.9"))
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `don't find higher version on higher but shorter version`() {
        Assertions.assertFalse(versionCheckerUtils.compareWithCurrentVersion("2.2.2", currentVersion = "2.3"))
        verify(uiInteraction, times(0)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `don't find higher version if on next RC`() {
        Assertions.assertFalse(versionCheckerUtils.compareWithCurrentVersion("2.2.2", currentVersion = "2.3-RC"))
        verify(uiInteraction, times(0)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `warn on beta`() {
        Assertions.assertTrue(versionCheckerUtils.compareWithCurrentVersion("2.2.2", currentVersion = "2.2-beta1"))
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun `warn on rc`() {
        Assertions.assertTrue(versionCheckerUtils.compareWithCurrentVersion("2.2.0", currentVersion = "2.2-rc1"))
        verify(uiInteraction, times(1)).addNotification(anyInt(), anyString(), anyInt())
    }

    @BeforeEach
    fun `set time`() {
        `when`(dateUtil.now()).thenReturn(10000000000L)
        assertThat(dateUtil.now()).isEqualTo(10000000000L)

        `when`(rh.gs(anyInt(), anyString())).thenReturn("")
    }

}
