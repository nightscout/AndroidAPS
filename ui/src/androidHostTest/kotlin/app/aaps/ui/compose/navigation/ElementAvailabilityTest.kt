package app.aaps.ui.compose.navigation

import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.calibration.Calibration
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.interfaces.source.XDripSource
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
 *  - CGM_XDRIP → only when XDrip source plugin is enabled
 *  - CGM_DEX   → only when Dexcom/BYODA source plugin is enabled
 *  - CALIBRATION → when XDrip is enabled OR a non-default calibration override plugin is active
 *  - FILL → only when the pump can be refilled (not on patch pumps)
 *  - BATTERY_CHANGE → when the pump battery is replaceable OR battery change logging is enabled
 *  - everything else → always available
 */
class ElementAvailabilityTest : TestBase() {

    @Mock lateinit var xDripSource: XDripSource
    @Mock lateinit var dexcomBoyda: DexcomBoyda
    @Mock lateinit var activePlugin: ActivePlugin

    private lateinit var elementAvailability: ElementAvailability

    @BeforeEach
    fun setUp() {
        elementAvailability = ElementAvailability(xDripSource, dexcomBoyda, activePlugin)
    }

    // -------- CGM_XDRIP --------

    @Test
    fun cgmXdrip_isAvailable_whenXDripEnabled() {
        whenever(xDripSource.isEnabled()).thenReturn(true)
        assertThat(elementAvailability.isAvailable(ElementType.CGM_XDRIP)).isTrue()
    }

    @Test
    fun cgmXdrip_isUnavailable_whenXDripDisabled() {
        whenever(xDripSource.isEnabled()).thenReturn(false)
        assertThat(elementAvailability.isAvailable(ElementType.CGM_XDRIP)).isFalse()
    }

    // -------- CGM_DEX --------

    @Test
    fun cgmDex_isAvailable_whenDexcomEnabled() {
        whenever(dexcomBoyda.isEnabled()).thenReturn(true)
        assertThat(elementAvailability.isAvailable(ElementType.CGM_DEX)).isTrue()
    }

    @Test
    fun cgmDex_isUnavailable_whenDexcomDisabled() {
        whenever(dexcomBoyda.isEnabled()).thenReturn(false)
        assertThat(elementAvailability.isAvailable(ElementType.CGM_DEX)).isFalse()
    }

    // -------- CALIBRATION --------

    @Test
    fun calibration_isAvailable_whenXDripEnabled_evenWithoutOverride() {
        whenever(xDripSource.isEnabled()).thenReturn(true)
        whenever(activePlugin.activeCalibration).thenReturn(mock<Calibration>())
        assertThat(elementAvailability.isAvailable(ElementType.CALIBRATION)).isTrue()
    }

    @Test
    fun calibration_isAvailable_whenOverrideActive_evenWithoutXDrip() {
        // Build the helper-stubbed plugin first; calling realCalibrationPlugin() inside
        // whenever() trips Mockito's "unfinished stubbing" detector because the helper itself
        // calls whenever() on the mock it constructs.
        val plugin = realCalibrationPlugin(defaultPlugin = false)
        whenever(xDripSource.isEnabled()).thenReturn(false)
        whenever(activePlugin.activeCalibration).thenReturn(plugin)
        assertThat(elementAvailability.isAvailable(ElementType.CALIBRATION)).isTrue()
    }

    @Test
    fun calibration_isUnavailable_whenXDripDisabled_andOverrideIsDefaultPlugin() {
        val plugin = realCalibrationPlugin(defaultPlugin = true)
        whenever(xDripSource.isEnabled()).thenReturn(false)
        whenever(activePlugin.activeCalibration).thenReturn(plugin)
        assertThat(elementAvailability.isAvailable(ElementType.CALIBRATION)).isFalse()
    }

    @Test
    fun calibration_isUnavailable_whenXDripDisabled_andActivePluginIsNotPluginBase() {
        // The override check casts via `as? PluginBase` — a Calibration impl that isn't a
        // PluginBase (e.g., a bare lambda/test double in some future codepath) must safely
        // resolve to "no override".
        whenever(xDripSource.isEnabled()).thenReturn(false)
        whenever(activePlugin.activeCalibration).thenReturn(mock<Calibration>())
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
        val gated = setOf(ElementType.CGM_XDRIP, ElementType.CGM_DEX, ElementType.CALIBRATION, ElementType.FILL, ElementType.BATTERY_CHANGE)
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
