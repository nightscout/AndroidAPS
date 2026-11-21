package app.aaps.plugins.source

import app.aaps.core.interfaces.source.CalibrationMode
import app.aaps.plugins.source.cgmapps.Calibration
import app.aaps.plugins.source.cgmapps.CgmApp
import app.aaps.plugins.source.cgmapps.CgmAppCatalog
import app.aaps.plugins.source.cgmapps.CgmApps
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DexcomPluginTest : TestBaseWithProfile() {

    private lateinit var dexcomPlugin: DexcomPlugin
    private val catalog: CgmAppCatalog = mock()

    @BeforeEach
    fun setup() {
        whenever(catalog.apps).thenReturn(
            CgmApps(
                apps = listOf(
                    CgmApp(CgmApps.DEXCOM_G6, "Dexcom G6", listOf("com.dexcom.g6", "com.dexcom.g6.region1.mmol")),
                    CgmApp(CgmApps.DEXCOM_G7, "Dexcom G7", listOf("com.dexcom.g7")),
                    CgmApp("dexcom-stelo", "Dexcom Stelo", listOf("com.dexcom.stelo"))
                )
            )
        )
        dexcomPlugin = DexcomPlugin(rh, aapsLogger, context, config, preferences, mock(), catalog)
    }

    /** What the CGM button opens: the G6 and G7 apps of the catalog, in every region, and no other Dexcom app. */
    @Test
    fun `the app packages are the G6 and G7 apps of the catalog`() {
        assertThat(dexcomPlugin.appPackages).containsExactly("com.dexcom.g6", "com.dexcom.g6.region1.mmol", "com.dexcom.g7").inOrder()
    }

    private val g6 = CgmApp(CgmApps.DEXCOM_G6, "Dexcom G6", listOf("com.dexcom.g6"), calibration = Calibration.APP)

    @Test
    fun `requiredPermissions should include dexcom permission when app is installed`() {
        whenever(catalog.firstInstalled(CgmApps.DEXCOM_G6, CgmApps.DEXCOM_G7)).thenReturn(g6)

        val allPermissions = dexcomPlugin.requiredPermissions().flatMap { it.permissions }
        assertThat(allPermissions).contains(DexcomPlugin.PERMISSION)
    }

    @Test
    fun `requiredPermissions should be empty when no dexcom app is installed`() {
        val allPermissions = dexcomPlugin.requiredPermissions().flatMap { it.permissions }
        assertThat(allPermissions).doesNotContain(DexcomPlugin.PERMISSION)
    }

    /** The Dexcom app takes optional calibrations: the calibrate button opens it, and AAPS does not correct on top. */
    @Test
    fun `the installed Dexcom app calibrates`() {
        whenever(catalog.firstInstalled(CgmApps.DEXCOM_G6, CgmApps.DEXCOM_G7)).thenReturn(g6)

        assertThat(dexcomPlugin.calibrationMode).isEqualTo(CalibrationMode.OWN_APP)
    }

    /** No Dexcom app: nothing to open, so the calibrate button is AAPS's again. */
    @Test
    fun `without a Dexcom app AAPS may calibrate`() {
        assertThat(dexcomPlugin.calibrationMode).isEqualTo(CalibrationMode.AAPS)
    }
}
