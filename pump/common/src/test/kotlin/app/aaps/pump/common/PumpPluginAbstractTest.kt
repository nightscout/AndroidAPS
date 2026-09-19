package app.aaps.pump.common

import android.content.Context
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.common.data.PumpStatus
import app.aaps.pump.common.defs.PumpDriverState
import app.aaps.pump.common.driver.PumpDriverConfiguration
import app.aaps.pump.common.sync.PumpSyncStorage
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.Date

/**
 * The parts of [PumpPluginAbstract] every driver inherits: the state predicates the command queue
 * reads before it sends anything, and the flows that carry pump readings up to the UI. A driver that
 * subclasses this gets these unchanged, so an error here is an error in every pump at once.
 */
internal class PumpPluginAbstractTest {

    private class TestStatus : PumpStatus(PumpType.GENERIC_AAPS) {

        override val errorInfo: String? = null
    }

    private val status = TestStatus()

    private class TestPump(
        private val statusData: PumpStatus,
        rh: ResourceHelper,
        aapsLogger: AAPSLogger,
        preferences: Preferences,
        commandQueue: CommandQueue,
        rxBus: RxBus,
        context: Context,
        pumpSync: PumpSync,
        pumpSyncStorage: PumpSyncStorage,
        decimalFormatter: DecimalFormatter,
        dateUtil: DateUtil,
        bolusProgressData: BolusProgressData
    ) : PumpPluginAbstract(
        pluginDescription = PluginDescription().mainType(PluginType.PUMP).pluginName(TextRef.AndroidRes(R.string.pump_status_ready)),
        pumpType = PumpType.GENERIC_AAPS,
        rh = rh,
        aapsLogger = aapsLogger,
        preferences = preferences,
        commandQueue = commandQueue,
        rxBus = rxBus,
        context = context,
        pumpSync = pumpSync,
        pumpSyncStorage = pumpSyncStorage,
        pumpDriverConfigurationInternal = mock(),
        decimalFormatter = decimalFormatter,
        dateUtil = dateUtil,
        pumpEnactResultProvider = { mock() },
        bolusProgressData = bolusProgressData
    ) {

        override fun initPumpStatusData() {}
        override fun onStartScheduledPumpActions() {}
        override val serviceClass: Class<*>? = null
        override val pumpStatusData: PumpStatus get() = statusData

        override fun generateTempId(objectA: Any): Long = 0L
        override fun serialNumber(): String = "TEST"

        var bolusesDelivered = 0
        override fun deliverBolus(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
            bolusesDelivered++
            return mock()
        }

        override fun triggerUIChange() {}

        override suspend fun getPumpStatus(reason: String) {}

        fun setState(state: PumpDriverState) {
            pumpState = state
        }
    }

    private fun pump() = TestPump(
        statusData = status,
        rh = mock(), aapsLogger = mock(), preferences = mock(), commandQueue = mock(),
        rxBus = mock(), context = mock(), pumpSync = mock(), pumpSyncStorage = mock(),
        decimalFormatter = mock(), dateUtil = mock(), bolusProgressData = mock()
    )

    // The queue asks these before deciding whether to connect, wait, or refuse. Busy and Suspended are
    // both "connected", so a queue seeing them must wait rather than start a new connection.
    @Test fun `the state predicates follow the driver state`() {
        val sut = pump()

        sut.setState(PumpDriverState.Connected)
        assertThat(sut.isConnected()).isTrue()
        assertThat(sut.isBusy()).isFalse()
        assertThat(sut.isSuspended()).isFalse()
        assertThat(sut.isInitialized()).isFalse()

        sut.setState(PumpDriverState.Busy)
        assertThat(sut.isBusy()).isTrue()
        assertThat(sut.isConnected()).isTrue()
        assertThat(sut.isInitialized()).isTrue()

        sut.setState(PumpDriverState.Suspended)
        assertThat(sut.isSuspended()).isTrue()
        assertThat(sut.isConnected()).isTrue()

        sut.setState(PumpDriverState.Connecting)
        assertThat(sut.isConnecting()).isTrue()
        assertThat(sut.isConnected()).isFalse()

        sut.setState(PumpDriverState.Disconnected)
        assertThat(sut.isConnected()).isFalse()
        assertThat(sut.isConnecting()).isFalse()
        assertThat(sut.isInitialized()).isFalse()
    }

    @Test fun `a fresh driver is not initialized`() {
        val sut = pump()

        assertThat(sut.isInitialized()).isFalse()
        assertThat(sut.isConnected()).isFalse()
        assertThat(sut.isHandshakeInProgress()).isFalse()
    }

    // The flows are the read path from the pump to the UI, and each one changes representation on the
    // way. A Date arriving as anything but epoch millis, or an insulin figure losing its type, would
    // be shown to the user as a wrong number rather than as an error.
    @Test fun `the reservoir flow carries the status value as insulin`() {
        val sut = pump()

        status.reservoirRemainingUnits = 123.5

        assertThat(sut.reservoirLevel.value.cU).isEqualTo(123.5)
    }

    @Test fun `the battery flow passes the status value straight through`() {
        val sut = pump()

        status.batteryRemaining = 64

        assertThat(sut.batteryLevel.value).isEqualTo(64)
    }

    @Test fun `the last bolus time flow converts the date to epoch millis`() {
        val sut = pump()

        status.lastBolusTime = Date(1_700_000_000_000L)

        assertThat(sut.lastBolusTime.value).isEqualTo(1_700_000_000_000L)
    }

    @Test fun `no bolus yet leaves the time and amount flows empty`() {
        val sut = pump()

        assertThat(sut.lastBolusTime.value).isNull()
        assertThat(sut.lastBolusAmount.value).isNull()
    }

    @Test fun `the last bolus amount flow wraps the value as insulin`() {
        val sut = pump()

        status.lastBolusAmount = 2.75

        assertThat(sut.lastBolusAmount.value?.cU).isEqualTo(2.75)
    }

    @Test fun `the last data time flow follows the recorded connection`() {
        val sut = pump()

        status.lastConnection = 999L

        assertThat(sut.lastDataTime.value).isEqualTo(999L)
    }

    // The base class answers for a driver that has not implemented these. Reporting 0 rather than
    // throwing keeps a half-written driver from taking the loop down with it.
    @Test fun `the unimplemented base reports a zero base basal rate`() {
        assertThat(pump().baseBasalRate.cU).isEqualTo(0.0)
    }

    @Test fun `the base class claims any profile is already set`() {
        assertThat(pump().isThisProfileSet(mock())).isTrue()
    }

    @Test fun `the pump identity comes from the type`() {
        val sut = pump()

        assertThat(sut.model()).isEqualTo(PumpType.GENERIC_AAPS)
        assertThat(sut.manufacturer()).isEqualTo(PumpType.GENERIC_AAPS.manufacturer())
    }
}
