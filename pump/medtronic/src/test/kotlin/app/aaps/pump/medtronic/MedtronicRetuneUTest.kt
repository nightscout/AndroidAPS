package app.aaps.pump.medtronic

import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkError
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkServiceState
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * #3253: after a tune-up failed (for example the pump was out of range) nothing tuned again. The pump was reported
 * as connecting, so the queue waited on every command until it timed out and never called connect(). Only the
 * "Wake and Tune Up" button or a new RileyLink connection brought the pump back.
 */
class MedtronicRetuneUTest : TestBaseWithProfile() {

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

    private lateinit var rileyLinkServiceData: RileyLinkServiceData
    private lateinit var plugin: MedtronicPumpPlugin

    @BeforeEach
    fun setup() {
        whenever(rileyLinkUtil.rileyLinkHistory).thenReturn(mutableListOf())
        rileyLinkServiceData = RileyLinkServiceData(aapsLogger, rileyLinkUtil, rxBus).apply { targetDevice = RileyLinkTargetDevice.MedtronicPump }
        plugin = MedtronicPumpPlugin(
            aapsLogger, rh, preferences, commandQueue, rxBus, context, medtronicUtil, MedtronicPumpStatus(preferences, rxBus, rileyLinkUtil),
            medtronicHistoryData, rileyLinkServiceData, serviceTaskExecutor, uiInteraction, notificationManager, dateUtil, pumpSync,
            pumpSyncStorage, decimalFormatter, pumpEnactResultProvider, bolusProgressData, { mock<WakeAndTuneTask>() },
            { mock<ResetRileyLinkConfigurationTask>() }, blePreCheck
        )
        // Private, set when the service binds.
        MedtronicPumpPlugin::class.java.getDeclaredField("isServiceSet").apply { isAccessible = true }.setBoolean(plugin, true)
    }

    @Test
    fun `after a failed tune-up the pump is not connecting, so the queue calls connect`() {
        stateSince(RileyLinkServiceState.PumpConnectorError, minutesAgo = 1)

        assertThat(plugin.isConnected()).isFalse()
        assertThat(plugin.isConnecting()).isFalse()
    }

    @Test
    fun `while a tune-up runs the pump is connecting`() {
        stateSince(RileyLinkServiceState.TuneUpDevice, minutesAgo = 1)

        assertThat(plugin.isConnecting()).isTrue()
    }

    @Test
    fun `connect tunes again when the tune-up failed more than 10 minutes ago`() {
        stateSince(RileyLinkServiceState.PumpConnectorError, minutesAgo = 11)

        plugin.connect("test")

        verify(serviceTaskExecutor).startTask(any())
    }

    @Test
    fun `connect does not tune again within 10 minutes of the failure`() {
        stateSince(RileyLinkServiceState.PumpConnectorError, minutesAgo = 5)

        plugin.connect("test")

        verify(serviceTaskExecutor, never()).startTask(any())
    }

    /** The queue calls connect() about once a second, and the task starts on another thread a little later. */
    @Test
    fun `connect called again and again starts only one tune-up`() {
        stateSince(RileyLinkServiceState.PumpConnectorError, minutesAgo = 11)

        repeat(5) { plugin.connect("test") }

        verify(serviceTaskExecutor, times(1)).startTask(any())
    }

    @Test
    fun `connect does nothing when the RileyLink is not in the error state`() {
        stateSince(RileyLinkServiceState.RileyLinkReady, minutesAgo = 11)

        plugin.connect("test")

        verify(serviceTaskExecutor, never()).startTask(any())
    }

    /** Sets [state] as the service would, and moves the time of the change [minutesAgo] into the past. */
    private fun stateSince(state: RileyLinkServiceState, minutesAgo: Long) {
        rileyLinkServiceData.setServiceState(state, if (state == RileyLinkServiceState.PumpConnectorError) RileyLinkError.TuneUpOfDeviceFailed else null)
        RileyLinkServiceData::class.java.getDeclaredField("lastServiceStateChange").apply { isAccessible = true }
            .setLong(rileyLinkServiceData, System.currentTimeMillis() - minutesAgo * 60 * 1000)
    }
}
