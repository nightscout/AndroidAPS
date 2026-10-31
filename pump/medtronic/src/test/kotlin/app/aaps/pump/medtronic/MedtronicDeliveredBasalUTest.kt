package app.aaps.pump.medtronic

import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.pump.common.PumpPluginAbstract
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import app.aaps.pump.common.hw.rileylink.service.tasks.ResetRileyLinkConfigurationTask
import app.aaps.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor
import app.aaps.pump.common.hw.rileylink.service.tasks.WakeAndTuneTask
import app.aaps.pump.common.sync.PumpSyncStorage
import app.aaps.pump.medtronic.data.MedtronicHistoryData
import app.aaps.pump.medtronic.driver.MedtronicPumpStatus
import app.aaps.pump.medtronic.util.MedtronicUtil
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock

/**
 * #2454: AAPS keeps what the pump reports as the running basal. A Medtronic rounds each rate to its step, and the
 * driver keeps the 24 hourly rates it read from the pump or sent to it.
 */
class MedtronicDeliveredBasalUTest : TestBaseWithProfile() {

    @Mock lateinit var rileyLinkUtil: RileyLinkUtil
    @Mock lateinit var medtronicUtil: MedtronicUtil
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var medtronicHistoryData: MedtronicHistoryData
    @Mock lateinit var serviceTaskExecutor: ServiceTaskExecutor
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var pumpSyncStorage: PumpSyncStorage
    @Mock lateinit var bolusProgressData: BolusProgressData
    @Mock lateinit var blePreCheck: BlePreCheck

    private lateinit var medtronicPumpStatus: MedtronicPumpStatus
    private lateinit var plugin: MedtronicPumpPlugin

    @BeforeEach
    fun setup() {
        medtronicPumpStatus = MedtronicPumpStatus(preferences, rxBus, rileyLinkUtil)
        plugin = MedtronicPumpPlugin(
            aapsLogger, rh, preferences, commandQueue, rxBus, context, medtronicUtil, medtronicPumpStatus, medtronicHistoryData,
            RileyLinkServiceData(aapsLogger, rileyLinkUtil, rxBus), serviceTaskExecutor, uiInteraction, notificationManager, dateUtil, pumpSync,
            pumpSyncStorage, decimalFormatter, pumpEnactResultProvider, bolusProgressData, { mock<WakeAndTuneTask>() },
            { mock<ResetRileyLinkConfigurationTask>() }, blePreCheck
        )
    }

    /** Set when the pump was read at start; protected, so set here as the start would. */
    private fun driverInitialized() {
        PumpPluginAbstract::class.java.getDeclaredField("isDriverInitialized").apply { isAccessible = true }.setBoolean(plugin, true)
    }

    @Test
    fun `the rates the pump runs are reported, one per hour`() {
        driverInitialized()
        medtronicPumpStatus.basalsByHour = DoubleArray(24) { hour -> if (hour < 6) 0.4 else 0.85 }

        val delivered = plugin.deliveredBasal()!!

        assertThat(delivered.map { it.timeAsSeconds }).containsExactlyElementsIn((0..23).map { it * 3600 }).inOrder()
        assertThat(delivered.map { it.value }).containsExactlyElementsIn((0..23).map { if (it < 6) 0.4 else 0.85 }).inOrder()
    }

    @Test
    fun `nothing is reported before the driver read the pump`() {
        medtronicPumpStatus.basalsByHour = DoubleArray(24) { 0.5 }

        assertThat(plugin.deliveredBasal()).isNull()
    }

    @Test
    fun `nothing is reported while the rates are not known`() {
        driverInitialized()
        medtronicPumpStatus.basalsByHour = null
        assertThat(plugin.deliveredBasal()).isNull()

        // All zero is what the driver holds when it could not read a profile from the pump.
        medtronicPumpStatus.basalsByHour = DoubleArray(24)
        assertThat(plugin.deliveredBasal()).isNull()
    }
}
