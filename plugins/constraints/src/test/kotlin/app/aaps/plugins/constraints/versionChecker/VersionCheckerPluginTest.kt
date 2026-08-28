package app.aaps.plugins.constraints.versionChecker

import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mock

class VersionCheckerPluginTest : TestBaseWithProfile() {

    @Mock lateinit var versionCheckerUtils: VersionCheckerUtils

    private lateinit var versionCheckerPlugin: VersionCheckerPlugin

    // SHTF eternal build: version-expiry disabled intentionally. See docs/SHTF_LOOP_RESILIENCE_PLAN.md
    // maxIOB must never be clamped because of version age, regardless of any stored expiry date.
    @Test
    fun applyMaxIOBConstraintsNeverClampsTest() {
        versionCheckerPlugin = VersionCheckerPlugin(aapsLogger, rh, preferences, versionCheckerUtils)

        val c1 = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        assertThat(versionCheckerPlugin.applyMaxIOBConstraints(c1).value()).isEqualTo(Double.MAX_VALUE)
        assertThat(c1.getReasons()).isEmpty()
    }
}
