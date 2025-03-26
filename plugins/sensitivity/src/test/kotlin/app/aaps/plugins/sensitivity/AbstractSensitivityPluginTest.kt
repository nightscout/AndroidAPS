package app.aaps.plugins.sensitivity

import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.Sensitivity
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.Mock

class AbstractSensitivityPluginTest : TestBase() {

    @Mock lateinit var pluginDescription: PluginDescription
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var preferences: Preferences

    private inner class SensitivityTestClass(pluginDescription: PluginDescription, aapsLogger: AAPSLogger, rh: ResourceHelper) :
        AbstractSensitivityPlugin(pluginDescription, aapsLogger, rh, preferences) {

        override fun detectSensitivity(ads: AutosensDataStore, fromTime: Long, toTime: Long): AutosensResult {
            return AutosensResult()
        }

        override val id: Sensitivity.SensitivityType
            get() = Sensitivity.SensitivityType.UNKNOWN

        override fun maxAbsorptionHours(): Double = 8.0
        override val isMinCarbsAbsorptionDynamic: Boolean = true
        override val isOref1: Boolean = true

        override fun configuration(): JSONObject = JSONObject()

        override fun applyConfiguration(configuration: JSONObject) {}
    }

    @Test
    fun fillResultTest() {
        val sut = SensitivityTestClass(pluginDescription, aapsLogger, rh)
        var ar = sut.fillResult(1.0, 1.0, "1", "1.2", "1", 12, 0.7, 1.2)
        assertThat(ar.ratio).isWithin(0.01).of(1.0)
        ar = sut.fillResult(1.2, 1.0, "1", "1.2", "1", 40, 0.7, 1.2)
        assertThat(ar.ratio).isWithin(0.01).of(1.16)
        ar = sut.fillResult(1.2, 1.0, "1", "1.2", "1", 50, 0.7, 1.2)
        assertThat(ar.ratio).isWithin(0.01).of(1.2)
        ar = sut.fillResult(1.2, 1.0, "1", "1.2", "1", 50, 0.7, 1.1)
        assertThat(ar.ratio).isWithin(0.01).of(1.1)
    }
}
