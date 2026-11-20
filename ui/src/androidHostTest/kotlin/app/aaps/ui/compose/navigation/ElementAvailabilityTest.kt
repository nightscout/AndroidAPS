package app.aaps.ui.compose.navigation

import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.calibration.Calibration
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.source.CalibrationMode
import app.aaps.core.interfaces.source.CgmDriver
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Verifies the gating rules in [ElementAvailability] — these decide whether a CGM/calibration
 * button is rendered at all. The contract is:
 *  - CGM → when the active BG source is a CGM driver (its screen) or reads a vendor app (opens it); never on a client
 *  - CALIBRATION → by the source's calibration mode: SOURCE always, OWN_APP with a known app, PUMP never, AAPS only
 *    with a non-default calibration plugin. On a client only with a non-default calibration plugin.
 *  - FILL → only when the pump can be refilled (not on patch pumps)
 *  - BATTERY_CHANGE → when the pump battery is replaceable OR battery change logging is enabled
 *  - everything else → always available
 */
class ElementAvailabilityTest : TestBase() {

    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var config: Config

    private lateinit var elementAvailability: ElementAvailability

    @BeforeEach
    fun setUp() {
        elementAvailability = ElementAvailability(activePlugin, config)
    }

    /** The active BG source, with what the buttons look at. A Mockito mock does not run interface defaults. */
    private fun source(mode: CalibrationMode = CalibrationMode.AAPS, appPackages: List<String> = emptyList(), driver: Boolean = false): BgSource {
        val source: BgSource = if (driver) mock<CgmDriver>() else mock()
        whenever(source.calibrationMode).thenReturn(mode)
        whenever(source.appPackages).thenReturn(appPackages)
        whenever(activePlugin.activeBgSource).thenReturn(source)
        return source
    }

    // -------- CGM --------

    @Test
    fun cgm_isAvailable_forACgmDriver() {
        source(driver = true)
        assertThat(elementAvailability.isAvailable(ElementType.CGM)).isTrue()
    }

    @Test
    fun cgm_isAvailable_forASourceWithAnApp() {
        source(appPackages = listOf("com.eveningoutpost.dexdrip"))
        assertThat(elementAvailability.isAvailable(ElementType.CGM)).isTrue()
    }

    @Test
    fun cgm_isUnavailable_forASourceWithNoAppAndNoScreen() {
        source()
        assertThat(elementAvailability.isAvailable(ElementType.CGM)).isFalse()
    }

    @Test
    fun cgm_isUnavailable_beforeABgSourceIsElected() {
        whenever(activePlugin.activeBgSource).thenThrow(IllegalStateException("No bg source selected"))
        assertThat(elementAvailability.isAvailable(ElementType.CGM)).isFalse()
    }

    // -------- CALIBRATION --------

    /** A CGM driver or xDrip takes the value from the AAPS dialog. */
    @Test
    fun calibration_isAvailable_whenTheSourceTakesCalibrations() {
        source(CalibrationMode.SOURCE)
        assertThat(elementAvailability.isAvailable(ElementType.CALIBRATION)).isTrue()
    }

    /** The button opens the vendor app, which calibrates. */
    @Test
    fun calibration_isAvailable_whenTheAppCalibrates() {
        source(CalibrationMode.OWN_APP, appPackages = listOf("com.dexcom.g7"))
        assertThat(elementAvailability.isAvailable(ElementType.CALIBRATION)).isTrue()
    }

    /** The app calibrates, but AAPS does not know which one to open: a button that does nothing is worse than none. */
    @Test
    fun calibration_isUnavailable_whenTheAppCalibratesButIsNotKnown() {
        source(CalibrationMode.OWN_APP)
        assertThat(elementAvailability.isAvailable(ElementType.CALIBRATION)).isFalse()
    }

    /** Calibrated on the pump: nothing in AAPS can take it, also not with the calibration plugin on. */
    @Test
    fun calibration_isUnavailable_whenThePumpCalibrates() {
        val plugin = realCalibrationPlugin(defaultPlugin = false)
        source(CalibrationMode.PUMP, appPackages = listOf("info.nightscout.android"))
        whenever(activePlugin.activeCalibration).thenReturn(plugin)
        assertThat(elementAvailability.isAvailable(ElementType.CALIBRATION)).isFalse()
    }

    @Test
    fun calibration_isAvailable_whenTheSourceDoesNotCalibrateButTheCalibrationPluginIsOn() {
        // Build the helper-stubbed plugin first; calling realCalibrationPlugin() inside
        // whenever() trips Mockito's "unfinished stubbing" detector because the helper itself
        // calls whenever() on the mock it constructs.
        val plugin = realCalibrationPlugin(defaultPlugin = false)
        source(CalibrationMode.AAPS)
        whenever(activePlugin.activeCalibration).thenReturn(plugin)
        assertThat(elementAvailability.isAvailable(ElementType.CALIBRATION)).isTrue()
    }

    @Test
    fun calibration_isUnavailable_whenNothingCalibrates() {
        val plugin = realCalibrationPlugin(defaultPlugin = true)
        source(CalibrationMode.AAPS)
        whenever(activePlugin.activeCalibration).thenReturn(plugin)
        assertThat(elementAvailability.isAvailable(ElementType.CALIBRATION)).isFalse()
    }

    @Test
    fun calibration_isUnavailable_whenTheActiveCalibrationIsNotAPlugin() {
        // The override check casts via `as? PluginBase` — a Calibration impl that isn't a
        // PluginBase (e.g., a bare lambda/test double in some future codepath) must safely
        // resolve to "no override".
        source(CalibrationMode.AAPS)
        whenever(activePlugin.activeCalibration).thenReturn(mock<Calibration>())
        assertThat(elementAvailability.isAvailable(ElementType.CALIBRATION)).isFalse()
    }

    // -------- on a client --------

    /** The CGM app and a CGM driver's screen are on the master's phone: a client has no CGM button. */
    @Test
    fun cgm_isUnavailable_onAClient() {
        whenever(config.AAPSCLIENT).thenReturn(true)
        source(appPackages = listOf("com.eveningoutpost.dexdrip"))
        assertThat(elementAvailability.isAvailable(ElementType.CGM)).isFalse()
        source(driver = true)
        assertThat(elementAvailability.isAvailable(ElementType.CGM)).isFalse()
    }

    /** A client calibrates only through the calibration plugin it syncs from the master. */
    @Test
    fun calibration_isAvailable_onAClient_withTheCalibrationPlugin() {
        whenever(config.AAPSCLIENT).thenReturn(true)
        val plugin = realCalibrationPlugin(defaultPlugin = false)
        whenever(activePlugin.activeCalibration).thenReturn(plugin)
        assertThat(elementAvailability.isAvailable(ElementType.CALIBRATION)).isTrue()
    }

    /** A source of its own on the client does not count: the client does not know how the master's CGM calibrates. */
    @Test
    fun calibration_isUnavailable_onAClient_withoutTheCalibrationPlugin() {
        whenever(config.AAPSCLIENT).thenReturn(true)
        val plugin = realCalibrationPlugin(defaultPlugin = true)
        source(CalibrationMode.SOURCE)
        whenever(activePlugin.activeCalibration).thenReturn(plugin)
        assertThat(elementAvailability.isAvailable(ElementType.CALIBRATION)).isFalse()
    }

    // -------- isCalibrationOverrideActive() --------

    @Test
    fun isCalibrationOverrideActive_falseForNonPluginBaseCalibration() {
        whenever(activePlugin.activeCalibration).thenReturn(mock<Calibration>())
        assertThat(elementAvailability.isCalibrationOverrideActive()).isFalse()
    }

    @Test
    fun isCalibrationOverrideActive_falseForDefaultPlugin() {
        val plugin = realCalibrationPlugin(defaultPlugin = true)
        whenever(activePlugin.activeCalibration).thenReturn(plugin)
        assertThat(elementAvailability.isCalibrationOverrideActive()).isFalse()
    }

    @Test
    fun isCalibrationOverrideActive_trueForRealOverridePlugin() {
        val plugin = realCalibrationPlugin(defaultPlugin = false)
        whenever(activePlugin.activeCalibration).thenReturn(plugin)
        assertThat(elementAvailability.isCalibrationOverrideActive()).isTrue()
    }

    // -------- FILL --------

    @Test
    fun fill_isAvailable_whenPumpCanRefill() {
        stubPump(refill = true)
        assertThat(elementAvailability.isAvailable(ElementType.FILL)).isTrue()
    }

    @Test
    fun fill_isUnavailable_onPatchPump() {
        // Patch pumps prime in their own activation wizard: no Refill capability.
        stubPump(refill = false)
        assertThat(elementAvailability.isAvailable(ElementType.FILL)).isFalse()
    }

    // -------- BATTERY_CHANGE --------

    @Test
    fun batteryChange_isAvailable_whenBatteryReplaceable() {
        stubPump(battery = true)
        assertThat(elementAvailability.isAvailable(ElementType.BATTERY_CHANGE)).isTrue()
    }

    @Test
    fun batteryChange_isAvailable_whenBatteryChangeLoggingEnabled() {
        // Omnipod Eros: the pod has no battery, but the user can log RileyLink battery changes.
        stubPump(battery = false, batteryLogging = true)
        assertThat(elementAvailability.isAvailable(ElementType.BATTERY_CHANGE)).isTrue()
    }

    @Test
    fun batteryChange_isUnavailable_onPatchPump() {
        stubPump(battery = false, batteryLogging = false)
        assertThat(elementAvailability.isAvailable(ElementType.BATTERY_CHANGE)).isFalse()
    }

    // -------- Default branch --------

    @Test
    fun nonGatedElementTypes_areAlwaysAvailable() {
        // Every type except the CGM/calibration and pump entries falls through to `true`. Looping
        // catches the case where someone adds a new entry to the gating `when` without
        // intentional defaults — this test will start failing for the new type.
        val gated = setOf(ElementType.CGM, ElementType.CALIBRATION, ElementType.FILL, ElementType.BATTERY_CHANGE)
        ElementType.entries
            .filterNot { it in gated }
            .forEach { type ->
                assertThat(elementAvailability.isAvailable(type)).isTrue()
            }
    }

    // -------- helpers --------

    private fun stubPump(refill: Boolean = false, battery: Boolean = false, batteryLogging: Boolean = false) {
        val description = PumpDescription().apply {
            isRefillingCapable = refill
            isBatteryReplaceable = battery
        }
        val pump = mock<PumpWithConcentration>()
        whenever(pump.pumpDescription).thenReturn(description)
        whenever(pump.isBatteryChangeLoggingEnabled()).thenReturn(batteryLogging)
        whenever(activePlugin.activePump).thenReturn(pump)
    }

    /**
     * Returns a mock that satisfies both [Calibration] and [PluginBase] — mirrors what real
     * calibration plugins look like (PluginBase subclasses implementing Calibration). The
     * `defaultPlugin` flag on the attached [PluginDescription] drives the override check.
     */
    private fun realCalibrationPlugin(defaultPlugin: Boolean): Calibration {
        val description = PluginDescription().apply { this.defaultPlugin = defaultPlugin }
        val plugin = mock<PluginBase>(extraInterfaces = arrayOf(Calibration::class))
        whenever(plugin.pluginDescription).thenReturn(description)
        return plugin as Calibration
    }
}
