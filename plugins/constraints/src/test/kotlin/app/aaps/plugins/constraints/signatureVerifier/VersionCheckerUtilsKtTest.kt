package app.aaps.plugins.constraints.signatureVerifier

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.versionChecker.VersionDefinition
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.constraints.versionChecker.VersionCheckerUtilsImpl
import app.aaps.plugins.constraints.versionChecker.numericVersionPart
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.anyString
import org.mockito.Mockito.`when`

@Suppress("SpellCheckingInspection")
class VersionCheckerUtilsKtTest : TestBase() {

    private lateinit var versionCheckerUtils: VersionCheckerUtilsImpl

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var config: Lazy<Config>
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var preferences: Preferences

    private fun generateSupportedVersions(): String =
        "{\n" +
            "  \"Latest versions\":\"\",\n" +
            "  \"30\": \"3.3.1.4\",\n" +
            "  \"31\": \"3.3.1.4\",\n" +
            "  \"32\": \"3.3.1.4\",\n" +
            "  \"33\": \"3.3.1.4\",\n" +
            "  \"34\": \"3.3.1.4\",\n" +
            "  \"35\": \"3.3.1.4\",\n" +
            "  \"36\": \"3.3.1.4\",\n" +
            "  \"37\": \"3.3.1.4\",\n" +
            "  \"38\": \"3.3.1.4\",\n" +
            "  \"Expire dates\": \"\",\n" +
            "  \"3.3.1.3\": \"2025-05-31\"\n" +
            "}"

    @BeforeEach fun setup() {
        val definition = VersionDefinition { JSONObject(generateSupportedVersions()) }
        versionCheckerUtils = VersionCheckerUtilsImpl(aapsLogger, preferences, rh, config, dateUtil, uiInteraction, definition)
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
        assertThat(versionCheckerUtils.evaluateVersion(newVersion = "2.2.3", currentVersion = "2.2.1")).isEqualTo(VersionCheckerUtilsImpl.VersionResult.NEWER_VERSION_AVAILABLE)
    }

    @Test
    fun `should find update2`() {
        assertThat(versionCheckerUtils.evaluateVersion(newVersion = "2.2.3", currentVersion = "2.2.1-dev")).isEqualTo(VersionCheckerUtilsImpl.VersionResult.NEWER_VERSION_AVAILABLE)
    }

    @Test
    fun `should find update3`() {
        assertThat(versionCheckerUtils.evaluateVersion(newVersion = "2.2.3", currentVersion = "2.1")).isEqualTo(VersionCheckerUtilsImpl.VersionResult.NEWER_VERSION_AVAILABLE)
    }

    @Test
    fun `should find update4`() {
        assertThat(versionCheckerUtils.evaluateVersion(newVersion = "2.2", currentVersion = "2.1.1")).isEqualTo(VersionCheckerUtilsImpl.VersionResult.NEWER_VERSION_AVAILABLE)
    }

    @Test
    fun `should find update5`() {
        assertThat(versionCheckerUtils.evaluateVersion(newVersion = "2.2.1", currentVersion = "2.2-dev")).isEqualTo(VersionCheckerUtilsImpl.VersionResult.NEWER_VERSION_AVAILABLE)
    }

    @Test
    fun `should find update6`() {
        assertThat(versionCheckerUtils.evaluateVersion(newVersion = "2.2.1", currentVersion = "2.2dev")).isEqualTo(VersionCheckerUtilsImpl.VersionResult.NEWER_VERSION_AVAILABLE)
    }

    @Test
    fun `should not find update on fourth version digit`() {
        assertThat(versionCheckerUtils.evaluateVersion(newVersion = "2.5.0", currentVersion = "2.5.0.1")).isEqualTo(VersionCheckerUtilsImpl.VersionResult.SAME_VERSION)
    }

    @Test
    fun `should not find update on personal version with same number`() {
        assertThat(versionCheckerUtils.evaluateVersion(newVersion = "2.5.0", currentVersion = "2.5.0-myversion")).isEqualTo(VersionCheckerUtilsImpl.VersionResult.SAME_VERSION)
    }

    @Test
    fun `find same version`() {
        assertThat(versionCheckerUtils.evaluateVersion("2.2.2", currentVersion = "2.2.2")).isEqualTo(VersionCheckerUtilsImpl.VersionResult.SAME_VERSION)
    }

    @Test
    fun `find higher version`() {
        assertThat(versionCheckerUtils.evaluateVersion("3.0.0", currentVersion = "2.2.2")).isEqualTo(VersionCheckerUtilsImpl.VersionResult.NEWER_VERSION_AVAILABLE)
    }

    @Test
    fun `find higher version with longer number`() {
        assertThat(versionCheckerUtils.evaluateVersion("3.0", currentVersion = "2.2.2")).isEqualTo(VersionCheckerUtilsImpl.VersionResult.NEWER_VERSION_AVAILABLE)
    }

    @Test
    fun `find higher version after RC`() {
        assertThat(versionCheckerUtils.evaluateVersion("3.0.0", currentVersion = "3.0-RC04")).isEqualTo(VersionCheckerUtilsImpl.VersionResult.NEWER_VERSION_AVAILABLE)
    }

    @Test
    fun `find higher version after RC 2 - typo`() {
        assertThat(versionCheckerUtils.evaluateVersion("3.0.0", currentVersion = "3.0RC04")).isEqualTo(VersionCheckerUtilsImpl.VersionResult.NEWER_VERSION_AVAILABLE)
    }

    @Test
    fun `find higher version after RC 3 - typo`() {
        assertThat(versionCheckerUtils.evaluateVersion("3.0.0", currentVersion = "3.RC04")).isEqualTo(VersionCheckerUtilsImpl.VersionResult.NEWER_VERSION_AVAILABLE)
    }

    @Test
    fun `find higher version after RC 4 - typo`() {
        assertThat(versionCheckerUtils.evaluateVersion("3.0.0", currentVersion = "3.0.RC04")).isEqualTo(VersionCheckerUtilsImpl.VersionResult.NEWER_VERSION_AVAILABLE)
    }

    @Test
    fun `find higher version on multi digit numbers`() {
        assertThat(versionCheckerUtils.evaluateVersion("3.7.12", currentVersion = "3.7.9")).isEqualTo(VersionCheckerUtilsImpl.VersionResult.NEWER_VERSION_AVAILABLE)
    }

    @Test
    fun `don't find higher version on higher but shorter version`() {
        assertThat(versionCheckerUtils.evaluateVersion("2.2.2", currentVersion = "2.3")).isEqualTo(VersionCheckerUtilsImpl.VersionResult.OLDER_VERSION)
    }

    @Test
    fun `don't find higher version if on next RC`() {
        assertThat(versionCheckerUtils.evaluateVersion("2.2.2", currentVersion = "2.3-RC")).isEqualTo(VersionCheckerUtilsImpl.VersionResult.OLDER_VERSION)
    }

    @Test
    fun `warn on beta`() {
        assertThat(versionCheckerUtils.evaluateVersion("2.2.2", currentVersion = "2.2-beta1")).isEqualTo(VersionCheckerUtilsImpl.VersionResult.NEWER_VERSION_AVAILABLE)
    }

    @Test
    fun `warn on rc`() {
        assertThat(versionCheckerUtils.evaluateVersion("2.2.0", currentVersion = "2.2-rc1")).isEqualTo(VersionCheckerUtilsImpl.VersionResult.NEWER_VERSION_AVAILABLE)
    }

    @BeforeEach
    fun `set time`() {
        `when`(dateUtil.now()).thenReturn(10000000000L)
        assertThat(dateUtil.now()).isEqualTo(10000000000L)

        `when`(rh.gs(anyInt(), anyString())).thenReturn("")
    }

}
