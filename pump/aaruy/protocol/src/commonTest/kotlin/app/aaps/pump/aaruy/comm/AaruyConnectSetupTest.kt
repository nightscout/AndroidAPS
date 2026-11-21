package app.aaps.pump.aaruy.comm

import app.aaps.core.interfaces.pump.ble.AdapterState
import app.aaps.shared.tests.AAPSLoggerTest
import app.aaps.core.interfaces.pump.ble.Advertisement
import app.aaps.core.interfaces.pump.ble.BleBonding
import app.aaps.core.interfaces.pump.ble.BleCentral
import app.aaps.core.interfaces.pump.ble.BleFailure
import app.aaps.core.interfaces.pump.ble.BleLink
import app.aaps.core.interfaces.pump.ble.BleResult
import app.aaps.core.interfaces.pump.ble.BondState
import app.aaps.core.interfaces.pump.ble.CharacteristicId
import app.aaps.core.interfaces.pump.ble.CharacteristicProperty
import app.aaps.core.interfaces.pump.ble.ConnectOptions
import app.aaps.core.interfaces.pump.ble.DeviceId
import app.aaps.core.interfaces.pump.ble.GattServices
import app.aaps.core.interfaces.pump.ble.Incoming
import app.aaps.core.interfaces.pump.ble.LinkPriority
import app.aaps.core.interfaces.pump.ble.LinkState
import app.aaps.core.interfaces.pump.ble.ScanFilter
import app.aaps.core.interfaces.pump.ble.Subscription
import app.aaps.core.interfaces.pump.ble.WriteType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * The driver asks to connect every few seconds until it is connected. On a real pump the setup includes pairing, which
 * waits for a button on the pump, so a request during the setup must leave the setup alone.
 */
class AaruyConnectSetupTest {

    private val scheduler = TestCoroutineScheduler()
    private val central = FakeCentral()
    private val listener = StatusListener()
    private val client = AaruyBleClient(AAPSLoggerTest(), central, StandardTestDispatcher(scheduler), scheduler.timeSource).also { it.setOnBleListener(listener) }

    private fun passTime(ms: Long) {
        scheduler.advanceTimeBy(ms)
        scheduler.runCurrent()
    }

    @Test
    fun askingAgainBeforeTheLinkIsUpChangesNothing() {
        central.connectAnswer = CompletableDeferred()
        client.connect(ADDRESS, NAME)
        client.connect(ADDRESS, NAME)
        passTime(1_000)

        assertEquals(1, central.connects)
    }

    /** Seen on a real pump: the user has to press its button, and the bond waits for that. */
    @Test
    fun askingAgainWhileThePairingWaitsChangesNothing() {
        central.bonded = false
        central.bondAnswer = CompletableDeferred()
        client.connect(ADDRESS, NAME)
        passTime(6_000)
        client.connect(ADDRESS, NAME)
        passTime(6_000)
        client.connect(ADDRESS, NAME)
        passTime(6_000)

        assertEquals(1, central.pins.size)
        assertEquals(0, central.connects)
    }

    /** Android gives the PIN to the system, so the user is never asked for it. */
    @Test
    fun aPumpThatIsNotBondedIsBondedWithThePinFromItsName() {
        central.bonded = false
        client.connect(ADDRESS, NAME)
        passTime(1_000)

        assertEquals<List<String?>>(listOf(AaruyBleClient.pinFromDeviceName(NAME)), central.pins)
        assertEquals(1, central.connects)
    }

    @Test
    fun aConnectThatFailsIsTriedAgainThenGivesUp() {
        central.connectAnswer = CompletableDeferred<BleResult<BleLink>>(BleResult.Failed(BleFailure(BleFailure.Kind.TIMEOUT)))
        client.connect(ADDRESS, NAME)
        passTime(10_000)

        assertEquals(3, central.connects)
        assertEquals(BleStatus.DISCONNECTED, listener.statuses.last())
    }

    @Test
    fun askingAgainAfterAllRetriesFailedStartsANewConnection() {
        central.connectAnswer = CompletableDeferred<BleResult<BleLink>>(BleResult.Failed(BleFailure(BleFailure.Kind.TIMEOUT)))
        client.connect(ADDRESS, NAME)
        passTime(10_000)
        assertEquals(3, central.connects)

        client.connect(ADDRESS, NAME)
        passTime(100)

        assertEquals(4, central.connects)
    }

    /** The link is reported up only when the setup is done: notifications on, after a large MTU. */
    @Test
    fun theLinkIsSetUpAsTheVendorLibraryDid() {
        client.connect(ADDRESS, NAME)
        passTime(6_000)

        val link = central.links.single()
        assertEquals(listOf("mtu", "subscribe ${AaruyBleClient.CHARACTERISTIC_NOTIFY}"), link.steps.take(2))
        assertEquals(BleStatus.CONNECTED, listener.statuses.last())
    }

    /** Seen on a real phone: no GATT event comes when Bluetooth is turned off. */
    @Test
    fun bluetoothTurnedOffEndsTheLink() {
        client.connect(ADDRESS, NAME)
        passTime(6_000)

        central.adapter.value = AdapterState.OFF
        passTime(100)

        assertTrue(central.links.single().closed)
        assertEquals(BleStatus.DISCONNECTED, listener.statuses.last())
    }

    @Test
    fun aLostLinkIsReported() {
        client.connect(ADDRESS, NAME)
        passTime(6_000)

        central.links.single().lose()
        passTime(100)

        assertEquals(BleStatus.DISCONNECTED, listener.statuses.last())
    }

    private class FakeLink(override val device: DeviceId) : BleLink {

        val steps = mutableListOf<String>()
        var closed = false
        private val _state = MutableStateFlow<LinkState>(LinkState.Up)
        override val state: StateFlow<LinkState> = _state
        override val services = GattServices(
            mapOf(
                AaruyBleClient.CHARACTERISTIC_WRITE to setOf(CharacteristicProperty.WRITE_WITHOUT_RESPONSE),
                AaruyBleClient.CHARACTERISTIC_NOTIFY to setOf(CharacteristicProperty.NOTIFY)
            )
        )
        private val received = Channel<Incoming>(Channel.UNLIMITED)
        override val incoming: Flow<Incoming> = received.receiveAsFlow()

        fun lose() {
            _state.value = LinkState.Lost(BleFailure(BleFailure.Kind.NO_LINK))
            received.close()
        }

        override fun maxWriteLength(type: WriteType): Int = 244
        override suspend fun subscribe(characteristic: CharacteristicId, kind: Subscription, timeout: Duration): BleResult<Unit> {
            steps += "subscribe $characteristic"
            return BleResult.Ok(Unit)
        }

        override suspend fun write(characteristic: CharacteristicId, data: ByteArray, type: WriteType, timeout: Duration): BleResult<Unit> {
            steps += "write"
            return BleResult.Ok(Unit)
        }

        override suspend fun read(characteristic: CharacteristicId, timeout: Duration): BleResult<ByteArray> = BleResult.Ok(ByteArray(0))

        override suspend fun requestMtu(wanted: Int): Int {
            steps += "mtu"
            return 247
        }

        override suspend fun requestPriority(priority: LinkPriority) = Unit

        override suspend fun close() {
            closed = true
            _state.value = LinkState.Closed
            received.close()
        }
    }

    private class FakeCentral : BleCentral {

        val adapter = MutableStateFlow(AdapterState.ON)
        override val adapterState: StateFlow<AdapterState> = adapter
        var bonded = true
        var bondAnswer: CompletableDeferred<BleResult<Unit>> = CompletableDeferred(BleResult.Ok(Unit))
        var connectAnswer: CompletableDeferred<BleResult<BleLink>>? = null
        val pins = mutableListOf<String?>()
        var connects = 0
        val links = mutableListOf<FakeLink>()

        override val bonding: BleBonding = object : BleBonding {
            override fun bondState(device: DeviceId): Flow<BondState> = adapter.map { if (bonded) BondState.BONDED else BondState.NONE }

            override suspend fun createBond(device: DeviceId, timeout: Duration, pin: String?): BleResult<Unit> {
                pins += pin
                return bondAnswer.await().also { if (it is BleResult.Ok) bonded = true }
            }

            override suspend fun removeBond(device: DeviceId): BleResult<Unit> = BleResult.Ok(Unit)
        }

        override fun scan(filter: ScanFilter): Flow<Advertisement> = emptyFlow()

        override suspend fun connect(device: DeviceId, options: ConnectOptions): BleResult<BleLink> {
            connects++
            connectAnswer?.let { return it.await() }
            return BleResult.Ok(FakeLink(device).also { links += it })
        }
    }

    private class StatusListener : AaruyBleListener {

        val statuses = mutableListOf<BleStatus>()

        override fun onBleStatusChanged(status: BleStatus) {
            statuses += status
        }

        override fun onReceiveBasicParamSetting(basicParam: BasicParam) = Unit
        override fun onReceiveBasalSetting(basalInfo: BasalInfo) = Unit
        override fun onReceiveCurrentStatus(statusInfo: StatusInfo) = Unit
        override fun onReceiveHistoryLog(logInfo: LogInfo) = Unit
        override fun onReceiveHistoryDaily(dailyInfo: DailyInfo) = Unit
        override fun onReceiveRealData(battery: Int, signal: Byte) = Unit
        override fun onReceiveErrorData(errorType: Int, error: Int) = Unit
        override fun onReceiveConfirmWarn(confirm: Int) = Unit
        override fun onRequestResult(success: Boolean, paramId: Int) = Unit
        override fun onBolusValueTooHigh(value: Double, maxValue: Double) = Unit
        override fun onInjectTimeError(time: Int) = Unit
        override fun onRunBasalFirst(currentState: Int) = Unit
        override fun onRunReplaceFirst(currentState: Int) = Unit
        override fun onRunTempBasalFirst(currentState: Int) = Unit
        override fun onBolusIsRunning(currentState: Int) = Unit
        override fun onLiquidShow() = Unit
        override fun onCurrentNoSuspend(currentState: Int) = Unit
        override fun onUnbindClearInfo() = Unit
        override fun onPumpClosedMode(currentMode: Int) = Unit
    }

    private companion object {

        const val ADDRESS = "D1:61:D0:00:00:01"
        const val NAME = "aaruy_X10001Y"
    }
}
