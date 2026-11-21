package app.aaps.plugins.constraints.signatureVerifier

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.versionChecker.VersionDefinition
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.constraints.versionChecker.VersionCheckerUtilsImpl
import app.aaps.plugins.constraints.versionChecker.keys.VersionCheckerLongKey
import app.aaps.plugins.constraints.versionChecker.numericVersionPart
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@Suppress("SpellCheckingInspection")
@OptIn(ExperimentalCoroutinesApi::class)
class VersionCheckerUtilsKtTest : TestBase() {

    private lateinit var versionCheckerUtils: VersionCheckerUtilsImpl

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var config: () -> Config
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var notificationManager: NotificationManager
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

    private val scope = TestScope(StandardTestDispatcher())

    @BeforeEach fun setup() {
        val definition = VersionDefinition { Json.parseToJsonElement(generateSupportedVersions()).jsonObject }
        versionCheckerUtils = VersionCheckerUtilsImpl(aapsLogger, preferences, rh, config, dateUtil, notificationManager, definition, scope)
    }

    // ---- the three sources of the allowed versions ----------------------------------------------------

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.content

    private fun fetchFromGithub(answer: suspend () -> String) {
        versionCheckerUtils.fetchGithubDefinition = answer
        versionCheckerUtils.fetchGithubDefinitionIfDue()
        scope.runCurrent()
    }

    @Test
    fun `the GitHub file wins over the bundled one, and remote config over both`() {
        fetchFromGithub { """{"38": "3.4.0.0", "3.3.1.3": "2025-06-30"}""" }

        assertThat(versionCheckerUtils.definition.text("38")).isEqualTo("3.4.0.0")
        assertThat(versionCheckerUtils.definition.text("30")).isEqualTo("3.3.1.4")

        versionCheckerUtils.setRemoteDefinition(Json.parseToJsonElement("""{"38": "3.5.0.0"}""").jsonObject)

        assertThat(versionCheckerUtils.definition.text("38")).isEqualTo("3.5.0.0")
        assertThat(versionCheckerUtils.definition.text("3.3.1.3")).isEqualTo("2025-06-30")
    }

    @Test
    fun `remote config still wins when GitHub answers after it`() {
        versionCheckerUtils.setRemoteDefinition(Json.parseToJsonElement("""{"38": "3.5.0.0"}""").jsonObject)

        fetchFromGithub { """{"38": "3.4.0.0"}""" }

        assertThat(versionCheckerUtils.definition.text("38")).isEqualTo("3.5.0.0")
    }

    @Test
    fun `a GitHub file that cannot be read keeps the bundled one`() {
        fetchFromGithub { throw IOException("offline") }

        assertThat(versionCheckerUtils.definition.text("38")).isEqualTo("3.3.1.4")
    }

    /** `definition.json` on the same branch is the old array form, kept for older versions. It must not be taken. */
    @Test
    fun `the old array form is not taken`() {
        fetchFromGithub { """[{"minAndroid":30,"maxAndroid":99,"supported":"9.9.9"}]""" }

        assertThat(versionCheckerUtils.definition.text("38")).isEqualTo("3.3.1.4")
    }

    /** The two warnings had one time between them, so a new-version warning held back the expiry warning. */
    @Test
    fun `an expiry warning is not held back by a new-version warning just given`() {
        val devConfig = mock<Config> {
            on { isDev() } doReturn true
            on { VERSION_NAME } doReturn "3.3.1.3"
        }
        whenever(config.invoke()).thenReturn(devConfig)
        whenever(dateUtil.isAfterNoon()).thenReturn(true)
        whenever(rh.gs(any<TextRef>(), anyVararg())).thenReturn("expires")
        val now = dateUtil.now()
        whenever(preferences.get(VersionCheckerLongKey.LastVersionCheckWarning)).thenReturn(now)
        whenever(preferences.get(VersionCheckerLongKey.LastExpiredWarning)).thenReturn(0L)

        versionCheckerUtils.triggerCheckVersion()

        val expiryPosted = Mockito.mockingDetails(notificationManager).invocations.any { it.method.name == "post" && it.arguments[0] == NotificationId.VERSION_EXPIRE }
        assertThat(expiryPosted).isTrue()
    }

    @Test
    fun `GitHub is read at most once a day after a success`() {
        var now = 10_000_000_000L
        whenever(dateUtil.now()).thenAnswer { now }
        var reads = 0
        val answer: suspend () -> String = { reads++; """{"38": "3.4.0.0"}""" }

        fetchFromGithub(answer)
        fetchFromGithub(answer)
        assertThat(reads).isEqualTo(1)

        now += 24 * 60 * 60 * 1000L
        fetchFromGithub(answer)
        assertThat(reads).isEqualTo(2)
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
    fun `should parse 4 digit version but only 3 digits are taken`() {
        assertThat(versionCheckerUtils.versionDigits("42.7.13.101")).asList().containsExactly(42, 7, 13).inOrder()
    }

    @Test
    fun `should parse 3 digit version with extra`() {
        assertThat(versionCheckerUtils.versionDigits("1.2.3-RC5")).asList().containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun `should parse version but only 3 digits are taken`() {
        assertThat(versionCheckerUtils.versionDigits("67.8.31.5.153.4.2")).asList().containsExactly(67, 8, 31).inOrder()
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
    fun `should strip 3 digit version RC long`() {
        assertThat("1.2.3-RC5".numericVersionPart()).isEqualTo("1.2.3")
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
    fun `should not find update on extra version digit beyond third`() {
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
        whenever(dateUtil.now()).thenReturn(10000000000L)
        assertThat(dateUtil.now()).isEqualTo(10000000000L)

        whenever(rh.gs(anyInt(), anyString())).thenReturn("")
    }

}
