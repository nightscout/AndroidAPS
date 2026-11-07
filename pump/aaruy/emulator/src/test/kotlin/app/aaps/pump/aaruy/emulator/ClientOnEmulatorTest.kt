package app.aaps.pump.aaruy.emulator

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Looper
import app.aaps.pump.aaruy.comm.AaruyBleClient
import app.aaps.pump.aaruy.comm.BasalInfo
import app.aaps.pump.aaruy.comm.BasalItem
import app.aaps.pump.aaruy.comm.BasalItems
import app.aaps.pump.aaruy.comm.BasicParam
import app.aaps.pump.aaruy.comm.AaruyBleListener
import app.aaps.pump.aaruy.comm.aaruyFrameKey
import app.aaps.core.interfaces.logging.AAPSLogger
import org.mockito.Mockito.mock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch

/**
 * The Kotlin protocol against the emulator, through the BLE transport. The vendor library passed the same scenarios
 * while it was still in the tests. It does not prove the firmware behaves the same.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClientOnEmulatorTest {

    private val log = CopyOnWriteArrayList<String>()
    private lateinit var util: AaruyBleClient
    private lateinit var transport: AaruyEmulatorBleTransport
    private lateinit var emulator: AaruyPumpEmulator

    private fun drain() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        Thread.sleep(20)
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 30_000
        while (!condition()) {
            assertTrue("Timeout waiting for: $what. status=${emulator.runningStatus} reservoir=${emulator.reservoir} log=${log.takeLast(12)}", System.currentTimeMillis() < end)
            drain()
        }
    }

    private fun logged(prefix: String) = log.any { it.startsWith(prefix) }

    private val listener = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(AaruyBleListener::class.java)) { _, method, args ->
        log.add(method.name + "(" + args.orEmpty().joinToString() + ")")
        Unit
    } as AaruyBleListener

    /** Also the one of an app run after a restart: it knows nothing of what the one before did. */
    private fun newClient() =
        AaruyBleClient(RuntimeEnvironment.getApplication(), mock(AAPSLogger::class.java), transport, Looper.getMainLooper()).also { it.setOnBleListener(listener) }

    @Before
    fun connect() {
        val context = RuntimeEnvironment.getApplication()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val device = adapter.getRemoteDevice(AaruyEmulatorBleTransport.DEVICE_ADDRESS)
        shadowOf(device).setBondState(BluetoothDevice.BOND_BONDED)

        emulator = AaruyPumpEmulator(keyProvider = { aaruyFrameKey() })
        transport = AaruyEmulatorBleTransport(emulator)
        util = newClient()
        util.bindDevice(device)
        waitFor("connected") { logged("onBleStatusChanged(CONNECTED") }
        util.setupBasicParamInfo(BasicParam(2.0, 10.0, 2, 20.0, 0, 3))
        waitFor("basic param") { logged("onReceiveBasicParamSetting") }
        util.getCurStatus()
        waitFor("status") { logged("onReceiveCurrentStatus") && logged("onReceiveRealData") }
    }

    @After
    fun disconnect() {
        transport.shutdown()
    }

    @Test
    fun connectGivesStatusAndParamsTheAarUnderstands() {
        assertTrue(log.toString(), log.any { it.contains("maxBolus=10.0") && it.contains("bolusStep=2") })
        assertTrue(log.toString(), log.any { it.contains("runningStatus=1") && it.contains("basalStatus=BasalStatus(") })
    }

    /** The driver writes from the main thread and from coroutines. A frame sent out of order is refused by the pump. */
    @Test
    fun writesFromManyThreadsKeepTheSequenceInOrder() {
        val start = CountDownLatch(1)
        val threads = List(8) {
            Thread {
                start.await()
                repeat(50) { util.getCurStatus() }
            }.apply { start() }
        }
        start.countDown()
        waitFor("all writes queued") { threads.none { it.isAlive } }
        var seen = -1
        waitFor("all frames sent") {
            val now = transport.writes.size
            val settled = now == seen
            seen = now
            repeat(10) { drain() }
            settled
        }
        assertEquals("no sequence errors", 0, emulator.rejectedFrames)
    }

    @Test
    fun bolusDeliversStepByStepAndEnds() {
        log.clear()
        util.setupAndStartBolus(0.15, 0.0, 0, false)
        waitFor("bolus running") { log.any { it.contains("runningStatus=3") } }
        waitFor("bolus done") { log.any { it.contains("runningStatus=3") && it.contains("state=2") } }
        waitFor("back on basal") { emulator.runningStatus == AaruyPumpEmulator.STATUS_BASAL }
        assertTrue("delivered 0.15: $log", log.any { it.contains("curBolusNow=150.0") })
        assertEquals("no sequence errors", 0, emulator.rejectedFrames)
    }

    @Test
    fun cancelledBolusPausesThenContinues() {
        util.setupAndStartBolus(5.0, 0.0, 0, false)
        waitFor("bolus running") { emulator.runningStatus == AaruyPumpEmulator.STATUS_BOLUS }
        waitFor("status seen by AAR") { log.any { it.contains("runningStatus=3") } }
        util.cancelBolus()
        waitFor("basal again") { emulator.runningStatus == AaruyPumpEmulator.STATUS_BASAL }
        waitFor("status seen") { log.any { it.contains("runningStatus=1") } }
    }

    /** The pump threads run only when the test drains this looper, so nothing is sent between the two calls. */
    @Test
    fun aStopWhileTheSettingIsOnTheWayKeepsTheStartFromGoingOut() {
        log.clear()
        util.setupAndStartBolus(5.0, 0.0, 0, false)
        util.cancelBolus()
        repeat(30) { drain() }
        assertFalse("no bolus ran: $log", log.any { it.contains("runningStatus=3") })
        assertEquals(AaruyPumpEmulator.STATUS_BASAL, emulator.runningStatus)
    }

    @Test
    fun aStartTheDriverGaveUpOnIsNeverSent() {
        log.clear()
        util.setupAndStartBolus(5.0, 0.0, 0, false)
        assertFalse("not sent yet", util.abandonBolusStart())
        repeat(30) { drain() }
        assertFalse("no bolus ran: $log", log.any { it.contains("runningStatus=3") })
        assertEquals(AaruyPumpEmulator.STATUS_BASAL, emulator.runningStatus)
    }

    /** The pump answers a start only with a status. A start that went out may run with no answer seen. */
    @Test
    fun aStartThatWentOutIsReportedAsSent() {
        util.setupAndStartBolus(0.5, 0.0, 0, false)
        waitFor("bolus running") { emulator.runningStatus == AaruyPumpEmulator.STATUS_BOLUS }
        assertTrue(util.abandonBolusStart())
    }

    /** A command asked on the old link must not go first on the new one: the driver has given up on it. */
    @Test
    fun aCommandWaitingAtALinkLossIsNotSentAfterTheReconnect() {
        util.setClosedMode(1)
        waitFor("closed mode") { emulator.closedMode == 1 }
        util.getCurStatus()
        util.startTempBasal(1.5, 30)
        transport.loseConnection()
        waitFor("link lost") { logged("onBleStatusChanged(DISCONNECTED") }
        log.clear()
        util.bindDevice(pumpDevice())
        waitFor("connected again") { logged("onBleStatusChanged(CONNECTED") }
        repeat(30) { drain() }
        assertEquals("the old temp basal was not sent", AaruyPumpEmulator.STATUS_BASAL, emulator.runningStatus)
    }

    @Test
    fun bolusAboveMaxIsRefusedByTheAar() {
        log.clear()
        util.setupAndStartBolus(11.0, 0.0, 0, false)
        waitFor("refused") { logged("onBolusValueTooHigh") }
        assertEquals(AaruyPumpEmulator.STATUS_BASAL, emulator.runningStatus)
    }

    @Test
    fun tempBasalStartsAndCancels() {
        log.clear()
        util.setClosedMode(1)
        waitFor("closed mode") { emulator.closedMode == 1 }
        util.startTempBasal(1.5, 30)
        waitFor("temp running") { log.any { it.contains("runningStatus=2") && it.contains("curValue=1.5") } }
        util.cancelTempBasal()
        waitFor("temp ended") { emulator.runningStatus == AaruyPumpEmulator.STATUS_BASAL }
    }

    @Test
    fun basalProfileIsEchoed() {
        log.clear()
        util.setupBasalInfo(BasalInfo(BasalItems(12.3, 2, listOf(BasalItem(0, 0.5), BasalItem(24, 0.275)))))
        waitFor("echo") { logged("onReceiveBasalSetting") }
        assertTrue(log.toString(), log.any { it.contains("BasalItem(startTime=24, basalSpeed=0.275)") && it.contains("injectPerDay=12.3") })
    }

    @Test
    fun historyAfterBolusHasTheBolus() {
        util.setupAndStartBolus(0.1, 0.0, 0, false)
        waitFor("bolus done") { emulator.runningStatus == AaruyPumpEmulator.STATUS_BASAL && log.any { it.contains("state=2") } }
        log.clear()
        util.getHistoryLogData(0)
        waitFor("history") { logged("onReceiveHistoryLog") }
        assertTrue(log.toString(), log.any { it.contains("type=2") && it.contains("data=100") })
        util.getHistoryDailyData(0)
        waitFor("daily") { logged("onReceiveHistoryDaily") }
    }

    @Test
    fun replaceThenStartBasalGoesThroughRestore() {
        log.clear()
        util.replaceDevice()
        waitFor("rewinding") { log.any { it.contains("runningStatus=5") } }
        waitFor("stopped") { log.any { it.contains("runningStatus=8") } }
        assertTrue("rewind event: $log", logged("onReceiveErrorData(17, 0"))
        util.startBasal()
        waitFor("back to not running, then basal by the AAR's own flow") { emulator.runningStatus == AaruyPumpEmulator.STATUS_BASAL }
    }

    /**
     * Review #60: the app was restarted after the rewind, and the client of the new app run does not know of it. It sent no
     * restore, so basal started only after a new rewind and prime. The driver tells it from the pump log.
     */
    @Test
    fun aClientToldOfAnEarlierRewindStartsBasalWithTheRestore() {
        util.replaceDevice()
        waitFor("stopped") { log.any { it.contains("runningStatus=8") } }
        util.closeLink()
        waitFor("closed") { logged("onBleStatusChanged(DISCONNECTED") }

        util = newClient()
        log.clear()
        util.bindDevice(pumpDevice())
        waitFor("connected") { logged("onBleStatusChanged(CONNECTED") }
        util.getCurStatus()
        waitFor("status") { log.any { it.contains("runningStatus=8") } }

        util.startBasal()
        waitWhile(500, Duration.ofSeconds(2))
        assertEquals("no restore without a rewind it knows of", AaruyPumpEmulator.STATUS_STOPPED, emulator.runningStatus)

        util.allowRestoreAfterRewind()
        util.startBasal()
        waitFor("basal") { emulator.runningStatus == AaruyPumpEmulator.STATUS_BASAL }
    }

    @Test
    fun sequenceErrorRecoversThroughSerialRead() {
        val manager = util.javaClass.getDeclaredField("link").apply { isAccessible = true }.get(util)
        manager.javaClass.getDeclaredField("sequence").apply { isAccessible = true }.set(manager, 500.toShort())
        log.clear()
        util.getCurStatus()
        waitFor("error seen") { logged("onReceiveErrorData(28, 128") }
        assertTrue(emulator.rejectedFrames > 0)
        waitFor("recovered") {
            log.clear()
            util.getCurStatus()
            drain()
            drain()
            drain()
            logged("onReceiveCurrentStatus")
        }
    }

    @Test
    fun alarmReachesTheAar() {
        log.clear()
        transport.pumpSends(emulator.raiseAlarm(0x8105))
        waitFor("alarm") { logged("onReceiveErrorData(5, 129") }
    }

    @Test
    fun lowReservoirRaisesAlarmOnce() {
        log.clear()
        // On the pump thread: its tick would overwrite a value set from here.
        transport.onPumpThread { emulator.reservoir = 19.0 }
        waitFor("low reservoir") { logged("onReceiveErrorData(3, 129") }
        Thread.sleep(600)
        drain()
        assertEquals(log.toString(), 1, log.count { it.startsWith("onReceiveErrorData(3, 129") })
    }

    @Test
    fun linkLossIsReported() {
        log.clear()
        transport.loseConnection()
        waitFor("reported") { logged("onBleStatusChanged(DISCONNECTED") }
        assertFalse(logged("onBleStatusChanged(CONNECTED"))
    }

    private fun pumpDevice(): BluetoothDevice =
        (RuntimeEnvironment.getApplication().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter.getRemoteDevice(AaruyEmulatorBleTransport.DEVICE_ADDRESS)

    /** Real time for the emulator, which runs on its own thread, and virtual time for the timeouts of the client. */
    private fun waitWhile(realMs: Long, virtual: Duration) {
        val end = System.currentTimeMillis() + realMs
        while (System.currentTimeMillis() < end) drain()
        shadowOf(Looper.getMainLooper()).idleFor(virtual)
        drain()
    }

    /** The command queue closes the link when it has nothing to send. It stays closed until the next connect. */
    @Test
    fun aClosedLinkStaysClosedUntilTheNextConnect() {
        log.clear()
        util.closeLink()
        waitFor("closed") { logged("onBleStatusChanged(DISCONNECTED") }
        waitWhile(500, Duration.ofSeconds(60))
        assertFalse("no link comes by itself: $log", logged("onBleStatusChanged(CONNECTED"))

        util.bindDevice(pumpDevice())
        waitFor("connected again") { logged("onBleStatusChanged(CONNECTED") }
    }

    /**
     * After its last try the link is DISCONNECTED, not CONNECTING. The command queue connects only a pump that is
     * neither, so a pump that stayed CONNECTING was never tried again.
     */
    @Test
    fun aConnectThatFailsEndsDisconnectedSoTheQueueCanTryAgain() {
        util.closeLink()
        waitFor("closed") { logged("onBleStatusChanged(DISCONNECTED") }
        transport.outOfRange = true
        log.clear()
        util.bindDevice(pumpDevice())
        waitFor("connecting") { logged("onBleStatusChanged(CONNECTING") }
        // Three tries of 15 s, with a second between them.
        repeat(4) { waitWhile(100, Duration.ofSeconds(20)) }
        assertTrue("given up: $log", logged("onBleStatusChanged(DISCONNECTED"))

        transport.outOfRange = false
        log.clear()
        util.bindDevice(pumpDevice())
        waitFor("connected on the next try") { logged("onBleStatusChanged(CONNECTED") }
    }

    /** A link closed while it connects can still report that it is up. Taken as new, it would open a link nobody asked for. */
    @Test
    fun aLinkClosedWhileItConnectsDoesNotComeBack() {
        util.closeLink()
        waitFor("closed") { logged("onBleStatusChanged(DISCONNECTED") }
        log.clear()
        util.bindDevice(pumpDevice())
        util.closeLink()
        // The emulator reports the link up after 200 ms.
        waitWhile(1_000, Duration.ofSeconds(5))
        assertFalse("no link: $log", logged("onBleStatusChanged(CONNECTED"))
    }
}
