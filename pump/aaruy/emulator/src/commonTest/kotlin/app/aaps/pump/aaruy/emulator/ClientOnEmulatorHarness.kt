package app.aaps.pump.aaruy.emulator

import app.aaps.core.interfaces.pump.ble.EmulatedBleCentral
import app.aaps.pump.aaruy.comm.AaruyBleClient
import app.aaps.pump.aaruy.comm.AaruyBleListener
import app.aaps.pump.aaruy.comm.BasalInfo
import app.aaps.pump.aaruy.comm.BasicParam
import app.aaps.pump.aaruy.comm.BleStatus
import app.aaps.pump.aaruy.comm.DailyInfo
import app.aaps.pump.aaruy.comm.LogInfo
import app.aaps.pump.aaruy.comm.StatusInfo
import app.aaps.pump.aaruy.comm.aaruyFrameKey
import app.aaps.shared.tests.AAPSLoggerTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * The client against the emulator, through the Bluetooth v2 emulated central. The emulator runs in real time on its own
 * threads; the client runs on a test dispatcher, so its timeouts take virtual time and nothing of it runs between two
 * calls of a test unless the test lets it ([drain]).
 */
abstract class ClientOnEmulatorHarness {

    protected val log = mutableListOf<String>()
    protected val scheduler = TestCoroutineScheduler()
    protected lateinit var central: EmulatedBleCentral
    protected lateinit var peripheral: AaruyEmulatorPeripheral
    protected lateinit var emulator: AaruyPumpEmulator
    protected lateinit var client: AaruyBleClient

    protected fun drain() {
        scheduler.advanceTimeBy(100)
        scheduler.runCurrent()
        runBlocking { delay(20) }
    }

    protected fun waitFor(what: String, condition: () -> Boolean) {
        val end = TimeSource.Monotonic.markNow()
        while (!condition()) {
            assertTrue(end.elapsedNow().inWholeSeconds < 30, "Timeout waiting for: $what. status=${emulator.runningStatus} reservoir=${emulator.reservoir} log=${log.takeLast(12)}")
            drain()
        }
    }

    /** Real time for the emulator, which runs on its own threads, and virtual time for the timeouts of the client. */
    protected fun waitWhile(realMs: Long, virtual: Duration) {
        val start = TimeSource.Monotonic.markNow()
        while (start.elapsedNow().inWholeMilliseconds < realMs) drain()
        scheduler.advanceTimeBy(virtual)
        drain()
    }

    protected fun logged(prefix: String) = log.any { it.startsWith(prefix) }

    /**
     * Also the one of an app run after a restart: it knows nothing of what the one before did. Its deadlines are in real
     * time, as the emulator's answers are: in virtual time a late answer of the emulator would always miss them.
     */
    protected fun newClient() =
        AaruyBleClient(AAPSLoggerTest(), central, StandardTestDispatcher(scheduler), TimeSource.Monotonic).also { it.setOnBleListener(listener) }

    protected fun connectClient() = client.connect(AaruyEmulatorPeripheral.DEVICE_ADDRESS, AaruyEmulatorPeripheral.DEVICE_NAME)

    /** A new emulated pump in place of the one there. */
    protected fun newPump(bonded: Boolean = true) {
        if (::peripheral.isInitialized) {
            peripheral.shutdown()
            central.remove(peripheral.device)
        }
        emulator = AaruyPumpEmulator(keyProvider = { aaruyFrameKey() })
        peripheral = AaruyEmulatorPeripheral(emulator)
        // The bond of the phone stays with the address when the device goes away.
        if (!bonded) runBlocking { central.bonding.removeBond(peripheral.device) }
        central.add(peripheral, bonded = bonded)
    }

    @BeforeTest
    fun connect() {
        central = EmulatedBleCentral()
        newPump()
        client = newClient()
        connectClient()
        waitFor("connected") { logged("onBleStatusChanged(CONNECTED") }
        client.setupBasicParamInfo(BasicParam(2.0, 10.0, 2, 20.0, 0, 3))
        waitFor("basic param") { logged("onReceiveBasicParamSetting") }
        client.getCurStatus()
        waitFor("status") { logged("onReceiveCurrentStatus") && logged("onReceiveRealData") }
    }

    @AfterTest
    fun disconnect() {
        peripheral.shutdown()
    }

    /** Each call as a line: the name, then the arguments. */
    private val listener = object : AaruyBleListener {

        private fun add(name: String, vararg args: Any?) {
            log += name + "(" + args.joinToString() + ")"
        }

        override fun onBleStatusChanged(status: BleStatus) = add("onBleStatusChanged", status)
        override fun onReceiveBasicParamSetting(basicParam: BasicParam) = add("onReceiveBasicParamSetting", basicParam)
        override fun onReceiveBasalSetting(basalInfo: BasalInfo) = add("onReceiveBasalSetting", basalInfo)
        override fun onReceiveCurrentStatus(statusInfo: StatusInfo) = add("onReceiveCurrentStatus", statusInfo)
        override fun onReceiveHistoryLog(logInfo: LogInfo) = add("onReceiveHistoryLog", logInfo)
        override fun onReceiveHistoryDaily(dailyInfo: DailyInfo) = add("onReceiveHistoryDaily", dailyInfo)
        override fun onReceiveRealData(battery: Int, signal: Byte) = add("onReceiveRealData", battery, signal)
        override fun onReceiveErrorData(errorType: Int, error: Int) = add("onReceiveErrorData", errorType, error)
        override fun onReceiveConfirmWarn(confirm: Int) = add("onReceiveConfirmWarn", confirm)
        override fun onRequestResult(success: Boolean, paramId: Int) = add("onRequestResult", success, paramId)
        override fun onBolusValueTooHigh(value: Double, maxValue: Double) = add("onBolusValueTooHigh", value, maxValue)
        override fun onInjectTimeError(time: Int) = add("onInjectTimeError", time)
        override fun onRunBasalFirst(currentState: Int) = add("onRunBasalFirst", currentState)
        override fun onRunReplaceFirst(currentState: Int) = add("onRunReplaceFirst", currentState)
        override fun onRunTempBasalFirst(currentState: Int) = add("onRunTempBasalFirst", currentState)
        override fun onBolusIsRunning(currentState: Int) = add("onBolusIsRunning", currentState)
        override fun onLiquidShow() = add("onLiquidShow")
        override fun onCurrentNoSuspend(currentState: Int) = add("onCurrentNoSuspend", currentState)
        override fun onUnbindClearInfo() = add("onUnbindClearInfo")
        override fun onPumpClosedMode(currentMode: Int) = add("onPumpClosedMode", currentMode)
    }
}
