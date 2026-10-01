package app.aaps.plugins.constraints.versionChecker

import app.aaps.plugins.constraints.ConstraintsStringsValues
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.shared.tests.TestBaseWithProfile
import app.aaps.shared.tests.generatedTextResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class VersionCheckerPluginTest : TestBaseWithProfile() {

    @Mock lateinit var versionCheckerUtils: VersionCheckerUtils

    private lateinit var versionCheckerPlugin: VersionCheckerPlugin

    @Test
    fun applyMaxIOBConstraintsTest() = runTest {
        versionCheckerPlugin = VersionCheckerPlugin(aapsLogger, generatedTextResolver("constraints" to ConstraintsStringsValues::textOf), preferences, versionCheckerUtils, config, dateUtil, mock())

        // No expiration
        whenever(preferences.get(LongComposedKey.AppExpiration, config.VERSION_NAME)).thenReturn(0)
        val c1 = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        assertThat(versionCheckerPlugin.applyMaxIOBConstraints(c1).value()).isEqualTo(Double.MAX_VALUE)

        // Waiting for expiration
        whenever(preferences.get(LongComposedKey.AppExpiration, config.VERSION_NAME)).thenReturn(now + 1000)
        val c2 = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        assertThat(versionCheckerPlugin.applyMaxIOBConstraints(c2).value()).isEqualTo(Double.MAX_VALUE)

        // Expired
        whenever(preferences.get(LongComposedKey.AppExpiration, config.VERSION_NAME)).thenReturn(now - 1000)
        val c3 = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        assertThat(versionCheckerPlugin.applyMaxIOBConstraints(c3).value()).isEqualTo(0.0)
    }
}
