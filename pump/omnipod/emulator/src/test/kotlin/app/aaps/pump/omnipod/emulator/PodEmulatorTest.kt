package app.aaps.pump.omnipod.emulator

import app.aaps.core.interfaces.pump.emulator.EmulatedAlarm
import app.aaps.core.interfaces.pump.emulator.EmulatedCommand
import app.aaps.pump.omnipod.common.bledriver.comm.session.ResponseUtil
import app.aaps.pump.omnipod.common.bledriver.pod.command.DeactivateCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.GetStatusCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.GetVersionCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.ProgramBasalCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.ProgramBolusCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.ProgramTempBasalCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.SetUniqueIdCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.StopDeliveryCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.SuspendDeliveryCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.base.Command
import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlarmType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BasalProgram
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BeepType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.DeliveryStatus
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodStatus
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ProgramReminder
import app.aaps.pump.omnipod.common.bledriver.pod.response.AlarmStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.DefaultStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.Response
import app.aaps.pump.omnipod.common.bledriver.pod.response.ResponseType
import app.aaps.pump.omnipod.common.bledriver.pod.response.SetUniqueIdResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.VersionResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Date

/**
 * The emulated pod against the driver's own command builders and answer parsers: each command is built as the driver
 * builds it, and each answer is read as the driver reads it. Time is a clock the test moves.
 */
class PodEmulatorTest {

    private var clock = 1_700_000_000_000L
    private var sequence = 0
    private lateinit var emulator: PodEmulator

    @BeforeEach
    fun setUp() {
        emulator = PodEmulator(now = { clock })
    }

    @Test
    fun `a new pod answers the version command as a filled pod`() {
        val version = send(GetVersionCommand.Builder().setSequenceNumber(next()).setUniqueId(GetVersionCommand.DEFAULT_UNIQUE_ID).build()) as VersionResponse

        assertEquals(PodStatus.FILLED, version.podStatus)
        assertEquals(4, version.firmwareVersionMajor.toInt())
        assertEquals(10, version.firmwareVersionMinor.toInt())
        assertEquals(emulator.state.lotNumber.toLong(), version.lotNumber)
        assertEquals(emulator.state.podSequenceNumber.toLong(), version.podSequenceNumber)
    }

    @Test
    fun `the activation goes through the pod states of a real pod`() {
        val unique = send(setUniqueId()) as SetUniqueIdResponse
        assertEquals(PodStatus.UID_SET, unique.podStatus)
        assertEquals(52, unique.numberOfEngagingClutchDrivePulses.toInt())
        assertEquals(10, unique.numberOfPrimePulses.toInt())
        assertEquals(ID.toLong(), unique.uniqueIdReceivedInCommand)

        var status = send(bolus(52 * 0.05, PRIME_RATE)) as DefaultStatusResponse
        assertEquals(PodStatus.ENGAGING_CLUTCH_DRIVE, status.podStatus)
        assertEquals(DeliveryStatus.PRIMING, status.deliveryStatus)
        clock += 52_000
        assertEquals(PodStatus.CLUTCH_DRIVE_ENGAGED, status().podStatus)

        status = send(basal(50)) as DefaultStatusResponse
        assertEquals(PodStatus.BASAL_PROGRAM_SET, status.podStatus)
        status = send(bolus(10 * 0.05, PRIME_RATE)) as DefaultStatusResponse
        assertEquals(PodStatus.PRIMING, status.podStatus)
        clock += 10_000
        status = status()
        assertEquals(PodStatus.RUNNING_ABOVE_MIN_VOLUME, status.podStatus)
        assertEquals(DeliveryStatus.BASAL_ACTIVE, status.deliveryStatus)
        // The priming is no bolus for the user, and the pod reports its reservoir from here on.
        assertTrue(emulator.snapshot(linkUp = false).boluses.isEmpty())
        assertEquals(1000, status.reservoirPulsesRemaining.toInt())
        assertEquals(62, status.totalPulsesDelivered.toInt())
    }

    @Test
    fun `the basal program is read hour by hour`() {
        activate(basalHundredths = 75)

        val basal = emulator.snapshot(linkUp = false).basal
        assertNotNull(basal)
        assertEquals(24, basal!!.size)
        basal.forEach { assertEquals(0.75, it, 0.0001) }
    }

    @Test
    fun `a bolus of an odd amount is given in full - one pulse each two seconds`() {
        activate()
        val status = send(bolus(1.15)) as DefaultStatusResponse
        assertEquals(DeliveryStatus.BOLUS_AND_BASAL_ACTIVE, status.deliveryStatus)
        assertEquals(23, status.bolusPulsesRemaining.toInt())

        clock += 10_000
        assertEquals(18, status().bolusPulsesRemaining.toInt())
        assertTrue(emulator.snapshot(linkUp = false).bolusRunning)

        clock += 36_000
        val state = emulator.snapshot(linkUp = false)
        assertFalse(state.bolusRunning)
        assertEquals(1, state.boluses.size)
        assertEquals(1.15, state.boluses.single(), 0.0001)
        assertEquals(0, status().bolusPulsesRemaining.toInt())
    }

    @Test
    fun `a stopped bolus reports the pulses it did not give`() {
        activate()
        send(bolus(2.0))
        clock += 8_000
        val stopped = send(stop(StopDeliveryCommand.DeliveryType.BOLUS)) as DefaultStatusResponse

        assertEquals(36, stopped.bolusPulsesRemaining.toInt())
        assertEquals(DeliveryStatus.BASAL_ACTIVE, stopped.deliveryStatus)
        assertEquals(0.2, emulator.snapshot(linkUp = false).boluses.single(), 0.0001)
        // The pod keeps saying it until the next bolus.
        assertEquals(36, status().bolusPulsesRemaining.toInt())
    }

    @Test
    fun `a temp basal runs at its rate and ends in its own time`() {
        activate()
        val status = send(tempBasal(1.0, 30)) as DefaultStatusResponse
        assertEquals(DeliveryStatus.TEMP_BASAL_ACTIVE, status.deliveryStatus)
        assertEquals(1.0, emulator.snapshot(linkUp = false).tempBasal!!.absolute!!, 0.0001)

        val before = emulator.state.totalPulses
        clock += 30 * 60_000
        assertNull(emulator.snapshot(linkUp = false).tempBasal)
        // 1 U/h for half an hour is 10 pulses.
        assertEquals(10, emulator.state.totalPulses - before)
    }

    @Test
    fun `a zero temp basal gives no pulse and a cancel ends it`() {
        activate()
        send(tempBasal(0.0, 60))
        assertEquals(0.0, emulator.snapshot(linkUp = false).tempBasal!!.absolute!!, 0.0001)
        val before = emulator.state.totalPulses
        clock += 20 * 60_000
        emulator.snapshot(linkUp = false)
        assertEquals(before, emulator.state.totalPulses)

        send(stop(StopDeliveryCommand.DeliveryType.TEMP_BASAL))
        assertNull(emulator.snapshot(linkUp = false).tempBasal)
    }

    @Test
    fun `a suspend stops the basal and a basal program starts it again`() {
        activate()
        val suspended = send(SuspendDeliveryCommand.Builder().setUniqueId(ID).setSequenceNumber(next()).setNonce(NONCE).setBeepType(BeepType.SILENT).build()) as DefaultStatusResponse
        assertEquals(DeliveryStatus.SUSPENDED, suspended.deliveryStatus)
        assertTrue(emulator.snapshot(linkUp = false).suspended)

        val resumed = send(basal(50)) as DefaultStatusResponse
        assertEquals(DeliveryStatus.BASAL_ACTIVE, resumed.deliveryStatus)
        assertFalse(emulator.snapshot(linkUp = false).suspended)
    }

    @Test
    fun `a lost command gives no answer and the pod does nothing`() {
        activate()
        val last = emulator.state.lastProgrammingSequence
        assertTrue(emulator.loseCommand(EmulatedCommand.BOLUS_START))

        assertNull(send(bolus(0.5)))
        assertFalse(emulator.snapshot(linkUp = false).bolusRunning)
        assertEquals(last, status().sequenceNumberOfLastProgrammingCommand.toInt())
        // Only the next one is lost.
        assertNotNull(send(bolus(0.5)))
    }

    @Test
    fun `a lost answer gives no answer but the pod ran the command and its status says so`() {
        activate()
        assertTrue(emulator.loseAnswer(EmulatedCommand.TEMP_BASAL_START))
        val command = tempBasal(1.0, 30)

        assertNull(send(command))
        assertNotNull(emulator.snapshot(linkUp = false).tempBasal)
        assertEquals(command.sequenceNumber.toInt(), status().sequenceNumberOfLastProgrammingCommand.toInt())
    }

    @Test
    fun `lost answers are used in the order they were set`() {
        activate()
        emulator.loseAnswer(EmulatedCommand.TEMP_BASAL_STOP)
        emulator.loseAnswer(EmulatedCommand.STATUS)
        send(tempBasal(1.0, 30))

        // The status answer waits for the cancel answer before it.
        assertNotNull(send(statusCommand()))
        assertNull(send(stop(StopDeliveryCommand.DeliveryType.TEMP_BASAL)))
        assertNull(send(statusCommand()))
        assertNotNull(send(statusCommand()))
    }

    @Test
    fun `an occlusion is a pod fault and every command gets the fault answer`() {
        activate()
        send(tempBasal(1.0, 30))
        assertTrue(emulator.raise(EmulatedAlarm.OCCLUSION))

        val fault = send(statusCommand()) as AlarmStatusResponse
        assertEquals(PodStatus.ALARM, fault.podStatus)
        assertEquals(AlarmType.ALARM_OCCLUDED, fault.alarmType)
        assertEquals(DeliveryStatus.SUSPENDED, fault.deliveryStatus)
        assertTrue(fault.occlusionAlarm)
        assertNull(emulator.snapshot(linkUp = false).tempBasal)
        // A pod with a fault takes no bolus.
        assertTrue(send(bolus(0.5)) is AlarmStatusResponse)
        assertTrue(emulator.snapshot(linkUp = false).boluses.isEmpty())
    }

    @Test
    fun `a bolus the pod stops gives a fault and counts the pulses it gave`() {
        activate()
        assertTrue(emulator.stopNextBolusAt(0.5))
        send(bolus(2.0))
        clock += 60_000

        val fault = send(statusCommand()) as AlarmStatusResponse
        assertEquals(AlarmType.ALARM_OCCLUDED, fault.alarmType)
        assertEquals(30, fault.bolusPulsesRemaining.toInt())
        assertEquals(0.5, emulator.snapshot(linkUp = false).boluses.single(), 0.0001)
    }

    @Test
    fun `an empty reservoir is a pod fault`() {
        activate()
        assertTrue(emulator.raise(EmulatedAlarm.EMPTY_RESERVOIR))
        assertEquals(AlarmType.ALARM_EMPTY_RESERVOIR, (send(statusCommand()) as AlarmStatusResponse).alarmType)
    }

    @Test
    fun `a pod reports no battery and no alarm before it runs`() {
        assertFalse(emulator.raise(EmulatedAlarm.OCCLUSION))
        activate()
        assertFalse(emulator.raise(EmulatedAlarm.LOW_BATTERY))
        assertNull(emulator.snapshot(linkUp = false).battery)
    }

    @Test
    fun `a deactivated pod answers once and then no more`() {
        activate()
        val status = send(DeactivateCommand.Builder().setUniqueId(ID).setSequenceNumber(next()).setNonce(NONCE).build()) as DefaultStatusResponse
        assertEquals(PodStatus.DEACTIVATED, status.podStatus)
        assertNull(send(statusCommand()))
    }

    @Test
    fun `a saved pod goes on with its bolus when it is read back`() {
        activate()
        send(bolus(2.0))
        clock += 10_000
        val saved = emulator.saveState()

        clock += 100_000
        val restored = PodEmulator(null, PodEmulator.restore(saved).state, now = { clock })
        val state = restored.snapshot(linkUp = false)
        assertFalse(state.bolusRunning)
        assertEquals(2.0, state.boluses.single(), 0.0001)
    }

    @Test
    fun `a new pod has a new address and is not paired`() {
        activate()
        emulator.paired(ByteArray(16))
        val old = emulator.state.address

        emulator.replaceWithNewPod()

        assertFalse(emulator.state.paired)
        assertTrue(emulator.state.address != old)
        assertNull(emulator.snapshot(linkUp = false).basal)
    }

    // ---- Helpers -------------------------------------------------------------------------------------------------

    private fun next(): Short = (sequence++ and 0x0f).toShort()

    /** Sends [command] as the driver encodes it and reads the answer as the driver does, without the envelope. */
    private fun send(command: Command): Response? =
        emulator.handleCommand(command.encoded)?.let { ResponseUtil.parseResponse(it.copyOfRange(6, it.size - 2)) }

    private fun status(): DefaultStatusResponse = send(statusCommand()) as DefaultStatusResponse

    private fun statusCommand() = GetStatusCommand.Builder().setUniqueId(ID).setSequenceNumber(next())
        .setStatusResponseType(ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE).build()

    private fun setUniqueId() = SetUniqueIdCommand.Builder().setSequenceNumber(next()).setUniqueId(ID)
        .setLotNumber(emulator.state.lotNumber).setPodSequenceNumber(emulator.state.podSequenceNumber)
        .setInitializationTime(Date(clock)).build()

    private fun bolus(units: Double, rate: Byte = BOLUS_RATE) = ProgramBolusCommand.Builder().setUniqueId(ID).setSequenceNumber(next())
        .setNonce(NONCE).setNumberOfUnits(units).setDelayBetweenPulsesInEighthSeconds(rate)
        .setProgramReminder(ProgramReminder(atStart = false, atEnd = false, atInterval = 0)).build()

    private fun basal(hundredths: Int) = ProgramBasalCommand.Builder().setUniqueId(ID).setSequenceNumber(next()).setNonce(NONCE)
        .setProgramReminder(ProgramReminder(atStart = false, atEnd = false, atInterval = 0))
        .setBasalProgram(BasalProgram(listOf(BasalProgram.Segment(0, 48, hundredths))))
        .setCurrentTime(Date(clock)).build()

    private fun tempBasal(rate: Double, minutes: Short) = ProgramTempBasalCommand.Builder().setUniqueId(ID).setSequenceNumber(next())
        .setNonce(NONCE).setProgramReminder(ProgramReminder(atStart = false, atEnd = false, atInterval = 0))
        .setRateInUnitsPerHour(rate).setDurationInMinutes(minutes).build()

    private fun stop(type: StopDeliveryCommand.DeliveryType) = StopDeliveryCommand.Builder().setUniqueId(ID).setSequenceNumber(next())
        .setNonce(NONCE).setDeliveryType(type).setBeepType(BeepType.SILENT).build()

    /** The commands of the activation wizard, with the pod's own priming times. */
    private fun activate(basalHundredths: Int = 50) {
        send(setUniqueId())
        send(bolus(52 * 0.05, PRIME_RATE))
        clock += 52_000
        send(basal(basalHundredths))
        send(bolus(10 * 0.05, PRIME_RATE))
        clock += 10_000
        assertEquals(PodStatus.RUNNING_ABOVE_MIN_VOLUME, status().podStatus)
    }

    private companion object {

        const val ID = 0x02420001
        const val NONCE = 1229869870

        /** Eighths of a second between two pulses, as the pod reports them: 2 s for a bolus, 1 s for the priming. */
        const val BOLUS_RATE: Byte = 16
        const val PRIME_RATE: Byte = 8
    }
}
