package app.aaps.plugins.constraints.versionChecker

import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.plugins.constraints.R
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

class VersionCheckerPluginTest : TestBaseWithProfile() {

    @Mock lateinit var versionCheckerUtils: VersionCheckerUtils

    private lateinit var versionCheckerPlugin: VersionCheckerPlugin

    @Test
    fun applyMaxIOBConstraintsTest() {
        versionCheckerPlugin = VersionCheckerPlugin(aapsLogger, rh, preferences, versionCheckerUtils, config, dateUtil)
        whenever(rh.gs(R.string.application_expired)).thenReturn("")

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
