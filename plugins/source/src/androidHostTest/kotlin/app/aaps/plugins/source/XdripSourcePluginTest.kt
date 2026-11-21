package app.aaps.plugins.source

import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.source.CalibrationMode
import app.aaps.core.interfaces.sync.XDripBroadcast
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.plugins.source.cgmapps.Calibration
import app.aaps.plugins.source.cgmapps.CgmApp
import app.aaps.plugins.source.cgmapps.CgmAppCatalog
import app.aaps.plugins.source.cgmapps.CgmApps
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class XdripSourcePluginTest : TestBaseWithProfile() {

    private lateinit var xdripSourcePlugin: XdripSourcePlugin
    private val catalog: CgmAppCatalog = mock()
    private val units: ProfileUtil = mock()
    private val broadcast: XDripBroadcast = mock()

    private val xdrip = CgmApp(CgmApps.XDRIP, "xDrip+", listOf("com.eveningoutpost.dexdrip"))
    private val juggluco = CgmApp(CgmApps.JUGGLUCO, "Juggluco", listOf("tk.glucodata"), calibration = Calibration.APP)

    @BeforeEach
    fun setup() {
        whenever(catalog.apps).thenReturn(CgmApps(apps = listOf(xdrip, juggluco)))
        xdripSourcePlugin = XdripSourcePlugin(rh, aapsLogger, preferences, config, mock(), catalog, units) { broadcast }
    }

    private fun installed(app: CgmApp?) {
        whenever(catalog.firstInstalled(CgmApps.XDRIP, CgmApps.JUGGLUCO)).thenReturn(app)
    }

    /** What the CGM button opens, from the CGM app catalog: xDrip+ first, then Juggluco, which sends the same broadcast. */
    @Test
    fun `the app packages are xDrip and Juggluco from the catalog`() {
        assertThat(xdripSourcePlugin.appPackages).containsExactly("com.eveningoutpost.dexdrip", "tk.glucodata").inOrder()
    }

    /** xDrip calibrates its own readings: the value from the AAPS dialog goes to xDrip, not to AAPS's calibration plugin. */
    @Test
    fun `xDrip takes the calibrations`() {
        installed(xdrip)
        assertThat(xdripSourcePlugin.calibrationMode).isEqualTo(CalibrationMode.SOURCE)
    }

    /** Juggluco alone: it calibrates in its own app, and sending to an xDrip that is not there would be lost. */
    @Test
    fun `Juggluco without xDrip calibrates in its own app`() {
        installed(juggluco)
        assertThat(xdripSourcePlugin.calibrationMode).isEqualTo(CalibrationMode.OWN_APP)
    }

    /** Another app sends the broadcast and AAPS does not know it: AAPS's calibration plugin may correct the readings. */
    @Test
    fun `an unknown sender leaves calibration to AAPS`() {
        installed(null)
        assertThat(xdripSourcePlugin.calibrationMode).isEqualTo(CalibrationMode.AAPS)
    }

    @Test
    fun `a calibration goes to xDrip in the user's units`() = runTest {
        whenever(units.fromMgdlToUnits(108.0)).thenReturn(6.0)
        whenever(broadcast.sendCalibration(any())).thenReturn(true)

        assertThat(xdripSourcePlugin.calibrate(108.0)).isNull()
        verify(broadcast).sendCalibration(6.0)
    }

    @Test
    fun `a calibration is refused with a reason when xDrip is not installed`() = runTest {
        whenever(broadcast.sendCalibration(any())).thenReturn(false)
        // doReturn, not whenever(rh.gs(...)): the test resolver throws for a plugins:source text while it is stubbed
        doReturn("not sent").whenever(rh).gs(SourceStrings.xdrip_calibration_not_sent)

        assertThat(xdripSourcePlugin.calibrate(108.0)).isEqualTo("not sent")
    }

    @Test
    fun `plugin is created`() {
        assertThat(xdripSourcePlugin).isNotNull()
    }

    @Test
    fun `preference screen content is provided`() {
        // exercises AbstractBgSourceWithSensorInsertLogPlugin.getPreferenceScreenContent
        assertThat(xdripSourcePlugin.getPreferenceScreenContent()).isInstanceOf(PreferenceSubScreenDef::class.java)
    }
}
