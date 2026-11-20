package app.aaps.plugins.source

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.CalibrationMode
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.source.cgmapps.Calibration
import app.aaps.plugins.source.cgmapps.CgmApp
import app.aaps.plugins.source.cgmapps.CgmAppCatalog
import app.aaps.plugins.source.cgmapps.CgmApps
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MM640GPluginTest : TestBase() {

    private lateinit var mM640gPlugin: MM640gPlugin

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var config: Config
    private val catalog: CgmAppCatalog = mock()

    private val uploader = CgmApp(CgmApps.MEDTRONIC_600_UPLOADER, "600 Series Uploader", listOf("info.nightscout.android"), calibration = Calibration.PUMP)

    @BeforeEach
    fun setup() {
        whenever(catalog.apps).thenReturn(CgmApps(apps = listOf(uploader)))
        mM640gPlugin = MM640gPlugin(rh, aapsLogger, preferences, config, mock(), catalog)
    }

    /** The CGM button opens the uploader that reads the pump. */
    @Test
    fun `the app package is the 600 Series Uploader`() {
        assertThat(mM640gPlugin.appPackages).containsExactly("info.nightscout.android")
    }

    /** A Guardian or Enlite sensor on a 600 series pump is calibrated on the pump, not in AAPS and not in an app. */
    @Test
    fun `the sensor is calibrated on the pump`() {
        whenever(catalog.firstInstalled(CgmApps.MEDTRONIC_600_UPLOADER)).thenReturn(uploader)
        assertThat(mM640gPlugin.calibrationMode).isEqualTo(CalibrationMode.PUMP)
    }
}
