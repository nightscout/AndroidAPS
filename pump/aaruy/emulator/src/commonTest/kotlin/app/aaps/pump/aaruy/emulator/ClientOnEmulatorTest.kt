package app.aaps.pump.aaruy.emulator

import app.aaps.core.interfaces.pump.emulator.EmulatedCommand
import app.aaps.pump.aaruy.comm.BasalInfo
import app.aaps.pump.aaruy.comm.BasalItem
import app.aaps.pump.aaruy.comm.BasalItems
import app.aaps.pump.aaruy.comm.BasicParam
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The Kotlin protocol against the emulator, through the Bluetooth v2 central. The vendor library passed the same
 * scenarios while it was still in the tests. It does not prove the firmware behaves the same.
 */
class ClientOnEmulatorTest : ClientOnEmulatorHarness() {

    @Test
    fun connectGivesStatusAndParamsTheAarUnderstands() {
        assertTrue(log.any { it.contains("maxBolus=10.0") && it.contains("bolusStep=2") }, log.toString())
        assertTrue(log.any { it.contains("runningStatus=1") && it.contains("basalStatus=BasalStatus(") }, log.toString())
    }

    /** A pump that is not bonded is bonded with the PIN from its name. The emulator refuses any other. */
    @Test
    fun aNewPumpIsBondedWithThePinFromItsName() {
        client.closeLink()
        waitFor("closed") { logged("onBleStatusChanged(DISCONNECTED") }
        newPump(bonded = false)
        log.clear()
        connectClient()
        waitFor("connected") { logged("onBleStatusChanged(CONNECTED") }
        assertEquals(1, peripheral.bonds)
    }

    @Test
    fun bolusDeliversStepByStepAndEnds() {
        log.clear()
        client.setupAndStartBolus(0.15, 0.0, 0, false)
        waitFor("bolus running") { log.any { it.contains("runningStatus=3") } }
        waitFor("bolus done") { log.any { it.contains("runningStatus=3") && it.contains("state=2") } }
        waitFor("back on basal") { emulator.runningStatus == AaruyPumpEmulator.STATUS_BASAL }
        assertTrue(log.any { it.contains("curBolusNow=150.0") }, "delivered 0.15: $log")
        assertEquals(0, emulator.rejectedFrames, "no sequence errors")
    }

    @Test
    fun cancelledBolusPausesThenContinues() {
        client.setupAndStartBolus(5.0, 0.0, 0, false)
        waitFor("bolus running") { emulator.runningStatus == AaruyPumpEmulator.STATUS_BOLUS }
        waitFor("status seen by AAR") { log.any { it.contains("runningStatus=3") } }
        client.cancelBolus()
        waitFor("basal again") { emulator.runningStatus == AaruyPumpEmulator.STATUS_BASAL }
        waitFor("status seen") { log.any { it.contains("runningStatus=1") } }
    }

    /** The client runs only when the test drains its dispatcher, so nothing is sent between the two calls. */
    @Test
    fun aStopWhileTheSettingIsOnTheWayKeepsTheStartFromGoingOut() {
        log.clear()
        client.setupAndStartBolus(5.0, 0.0, 0, false)
        client.cancelBolus()
        repeat(30) { drain() }
        assertFalse(log.any { it.contains("runningStatus=3") }, "no bolus ran: $log")
        assertEquals(AaruyPumpEmulator.STATUS_BASAL, emulator.runningStatus)
    }

    @Test
    fun aStartTheDriverGaveUpOnIsNeverSent() {
        log.clear()
        client.setupAndStartBolus(5.0, 0.0, 0, false)
        assertFalse(client.abandonBolusStart(), "not sent yet")
        repeat(30) { drain() }
        assertFalse(log.any { it.contains("runningStatus=3") }, "no bolus ran: $log")
        assertEquals(AaruyPumpEmulator.STATUS_BASAL, emulator.runningStatus)
    }

    /** The pump answers a start only with a status. A start that went out may run with no answer seen. */
    @Test
    fun aStartThatWentOutIsReportedAsSent() {
        client.setupAndStartBolus(0.5, 0.0, 0, false)
        waitFor("bolus running") { emulator.runningStatus == AaruyPumpEmulator.STATUS_BOLUS }
        assertTrue(client.abandonBolusStart())
    }

    /** A command asked on the old link must not go first on the new one: the driver has given up on it. */
    @Test
    fun aCommandWaitingAtALinkLossIsNotSentAfterTheReconnect() {
        client.setClosedMode(1)
        waitFor("closed mode") { emulator.closedMode == 1 }
        client.getCurStatus()
        client.startTempBasal(1.5, 30)
        peripheral.loseConnection()
        waitFor("link lost") { logged("onBleStatusChanged(DISCONNECTED") }
        log.clear()
        connectClient()
        waitFor("connected again") { logged("onBleStatusChanged(CONNECTED") }
        repeat(30) { drain() }
        assertEquals(AaruyPumpEmulator.STATUS_BASAL, emulator.runningStatus, "the old temp basal was not sent")
    }

    @Test
    fun bolusAboveMaxIsRefusedByTheAar() {
        log.clear()
        client.setupAndStartBolus(11.0, 0.0, 0, false)
        waitFor("refused") { logged("onBolusValueTooHigh") }
        assertEquals(AaruyPumpEmulator.STATUS_BASAL, emulator.runningStatus)
    }

    @Test
    fun tempBasalStartsAndCancels() {
        log.clear()
        client.setClosedMode(1)
        waitFor("closed mode") { emulator.closedMode == 1 }
        client.startTempBasal(1.5, 30)
        waitFor("temp running") { log.any { it.contains("runningStatus=2") && it.contains("curValue=1.5") } }
        client.cancelTempBasal()
        waitFor("temp ended") { emulator.runningStatus == AaruyPumpEmulator.STATUS_BASAL }
    }

    @Test
    fun basalProfileIsEchoed() {
        log.clear()
        client.setupBasalInfo(BasalInfo(BasalItems(12.3, 2, listOf(BasalItem(0, 0.5), BasalItem(24, 0.275)))))
        waitFor("echo") { logged("onReceiveBasalSetting") }
        assertTrue(log.any { it.contains("BasalItem(startTime=24, basalSpeed=0.275)") && it.contains("injectPerDay=12.3") }, log.toString())
    }

    @Test
    fun historyAfterBolusHasTheBolus() {
        client.setupAndStartBolus(0.1, 0.0, 0, false)
        waitFor("bolus done") { emulator.runningStatus == AaruyPumpEmulator.STATUS_BASAL && log.any { it.contains("state=2") } }
        log.clear()
        client.getHistoryLogData(0)
        waitFor("history") { logged("onReceiveHistoryLog") }
        assertTrue(log.any { it.contains("type=2") && it.contains("data=100") }, log.toString())
        client.getHistoryDailyData(0)
        waitFor("daily") { logged("onReceiveHistoryDaily") }
    }

    @Test
    fun replaceThenStartBasalGoesThroughRestore() {
        log.clear()
        client.replaceDevice()
        waitFor("rewinding") { log.any { it.contains("runningStatus=5") } }
        waitFor("stopped") { log.any { it.contains("runningStatus=8") } }
        assertTrue(logged("onReceiveErrorData(17, 0"), "rewind event: $log")
        client.startBasal()
        waitFor("back to not running, then basal by the AAR's own flow") { emulator.runningStatus == AaruyPumpEmulator.STATUS_BASAL }
    }

    /** Seen on a real pump: a stopped pump logs the rewind at once, the prime only when it leaves the stop. */
    @Test
    fun thePrimeIsLoggedOnlyWhenThePumpLeavesTheStop() {
        client.replaceDevice()
        waitFor("stopped") { log.any { it.contains("runningStatus=8") } }
        log.clear()
        client.pushPosition()
        waitFor("primed") { log.any { it.contains("runningStatus=6") } && emulator.runningStatus == AaruyPumpEmulator.STATUS_STOPPED }

        log.clear()
        client.getHistoryLogData(0)
        waitFor("history while stopped") { logged("onReceiveHistoryLog") }
        assertTrue(log.any { it.contains("type=4,") }, log.toString())
        assertFalse(log.any { it.contains("type=5,") }, log.toString())

        client.startBasal()
        waitFor("basal") { emulator.runningStatus == AaruyPumpEmulator.STATUS_BASAL }
        log.clear()
        client.getHistoryLogData(0)
        waitFor("history after the stop") { logged("onReceiveHistoryLog") }
        assertTrue(log.any { it.contains("type=5,") }, log.toString())
    }

    /**
     * Review #60: the app was restarted after the rewind, and the client of the new app run does not know of it. It sent no
     * restore, so basal started only after a new rewind and prime. The driver tells it from the pump log.
     */
    @Test
    fun aClientToldOfAnEarlierRewindStartsBasalWithTheRestore() {
        client.replaceDevice()
        waitFor("stopped") { log.any { it.contains("runningStatus=8") } }
        client.closeLink()
        waitFor("closed") { logged("onBleStatusChanged(DISCONNECTED") }

        client = newClient()
        log.clear()
        connectClient()
        waitFor("connected") { logged("onBleStatusChanged(CONNECTED") }
        client.getCurStatus()
        waitFor("status") { log.any { it.contains("runningStatus=8") } }

        client.startBasal()
        waitWhile(500, 2.seconds)
        assertEquals(AaruyPumpEmulator.STATUS_STOPPED, emulator.runningStatus, "no restore without a rewind it knows of")

        client.allowRestoreAfterRewind()
        client.startBasal()
        waitFor("basal") { emulator.runningStatus == AaruyPumpEmulator.STATUS_BASAL }
    }

    /**
     * The link counts as up only when the pump has told its sequence. It counted as up after a fixed 500 ms: frames the
     * pump took went out before the late answer, which then set the sequence back, and the next frame was refused
     * (error 28) with its request lost. That is how a rewind got lost in a CI run of
     * [aClientToldOfAnEarlierRewindStartsBasalWithTheRestore].
     */
    @Test
    fun aLateFirstAnswerDoesNotPutTheSequenceBack() {
        client.closeLink()
        waitFor("closed") { logged("onBleStatusChanged(DISCONNECTED") }
        newPump()
        emulator.answerDelayMs = 1500 // the first answer: the one with the pump's sequence
        log.clear()
        client = newClient()
        connectClient()
        waitFor("connected") { logged("onBleStatusChanged(CONNECTED") }
        client.setupBasicParamInfo(BasicParam(2.0, 10.0, 2, 20.0, 0, 3))
        client.getCurStatus()
        waitFor("status") { logged("onReceiveCurrentStatus") }
        waitWhile(500, 1.seconds)
        assertEquals(0, emulator.rejectedFrames, "frames refused for their sequence")
    }

    /**
     * Seen on a real pump: a frame refused for its number is not carried out, and the count read tells the number of the
     * last frame the pump took. So the lost status read and the refused real data read go out again. Before, neither
     * was ever answered.
     */
    @Test
    fun readsAfterALostFrameAreSentAgain() {
        // The answer to the time sync at connect also has a status.
        waitWhile(1500, 1.seconds)
        peripheral.loseCommand(EmulatedCommand.STATUS)
        log.clear()
        client.getCurStatus()
        waitFor("status answered") { logged("onReceiveCurrentStatus") && logged("onReceiveRealData") }
        assertTrue(emulator.rejectedFrames > 0, "the real data read was refused")
    }

    @Test
    fun alarmReachesTheAar() {
        log.clear()
        peripheral.pumpSends(emulator.raiseAlarm(0x8105))
        waitFor("alarm") { logged("onReceiveErrorData(5, 129") }
    }

    @Test
    fun lowReservoirRaisesAlarmOnce() {
        log.clear()
        // With the emulator to itself: its tick would overwrite a value set from here.
        peripheral.onPumpThread { emulator.reservoir = 19.0 }
        waitFor("low reservoir") { logged("onReceiveErrorData(3, 129") }
        runBlocking { delay(600) }
        drain()
        assertEquals(1, log.count { it.startsWith("onReceiveErrorData(3, 129") }, log.toString())
    }

    @Test
    fun linkLossIsReported() {
        log.clear()
        peripheral.loseConnection()
        waitFor("reported") { logged("onBleStatusChanged(DISCONNECTED") }
        assertFalse(logged("onBleStatusChanged(CONNECTED"))
    }

    /** The command queue closes the link when it has nothing to send. It stays closed until the next connect. */
    @Test
    fun aClosedLinkStaysClosedUntilTheNextConnect() {
        log.clear()
        client.closeLink()
        waitFor("closed") { logged("onBleStatusChanged(DISCONNECTED") }
        waitWhile(500, 60.seconds)
        assertFalse(logged("onBleStatusChanged(CONNECTED"), "no link comes by itself: $log")

        connectClient()
        waitFor("connected again") { logged("onBleStatusChanged(CONNECTED") }
    }

    /**
     * After its last try the link is DISCONNECTED, not CONNECTING. The command queue connects only a pump that is
     * neither, so a pump that stayed CONNECTING was never tried again.
     */
    @Test
    fun aConnectThatFailsEndsDisconnectedSoTheQueueCanTryAgain() {
        client.closeLink()
        waitFor("closed") { logged("onBleStatusChanged(DISCONNECTED") }
        peripheral.outOfRange = true
        log.clear()
        connectClient()
        waitFor("connecting") { logged("onBleStatusChanged(CONNECTING") }
        // Three tries of 15 s, with a second between them.
        repeat(4) { waitWhile(100, 20.seconds) }
        assertTrue(logged("onBleStatusChanged(DISCONNECTED"), "given up: $log")

        peripheral.outOfRange = false
        log.clear()
        connectClient()
        waitFor("connected on the next try") { logged("onBleStatusChanged(CONNECTED") }
    }

    /** A link closed while it connects can still report that it is up. Taken as new, it would open a link nobody asked for. */
    @Test
    fun aLinkClosedWhileItConnectsDoesNotComeBack() {
        client.closeLink()
        waitFor("closed") { logged("onBleStatusChanged(DISCONNECTED") }
        log.clear()
        connectClient()
        client.closeLink()
        // The emulator reports the link up after 200 ms.
        waitWhile(1_000, 5.seconds)
        assertFalse(logged("onBleStatusChanged(CONNECTED"), "no link: $log")
    }
}
