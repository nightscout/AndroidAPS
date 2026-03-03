package app.aaps.plugins.source

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class NSClientSourcePluginTest : TestBase() {

    private lateinit var nsClientSourcePlugin: NSClientSourcePlugin

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var config: Config

    @BeforeEach
    fun setup() {
        nsClientSourcePlugin = NSClientSourcePlugin(rh, aapsLogger, config)
    }

    @Test
    fun advancedFilteringSupported() {
        assertThat(nsClientSourcePlugin.advancedFilteringSupported()).isFalse()
    }

    @Test
    fun detectSourceTest() {
        nsClientSourcePlugin.detectSource(
            GV(
                timestamp = 10000L,
                value = 150.0,
                raw = null,
                noise = null,
                trendArrow = TrendArrow.FORTY_FIVE_DOWN,
                sourceSensor = SourceSensor.DEXCOM_G6_NATIVE
            )
        )
        assertThat(nsClientSourcePlugin.isAdvancedFilteringEnabled).isTrue()
        assertThat(nsClientSourcePlugin.lastBGTimeStamp).isEqualTo(10000L)
    }
}
