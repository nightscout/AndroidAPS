package app.aaps.pump.carelevo.emulator

import app.aaps.pump.carelevo.ble.commands.AdditionalPrimingCommand
import app.aaps.pump.carelevo.ble.commands.AlarmClearCommand
import app.aaps.pump.carelevo.ble.commands.AlertAlarmSetCommand
import app.aaps.pump.carelevo.ble.commands.AppAuthCommand
import app.aaps.pump.carelevo.ble.commands.BasalProgramCommand
import app.aaps.pump.carelevo.ble.commands.BolusCancelCommand
import app.aaps.pump.carelevo.ble.commands.BuzzModeCommand
import app.aaps.pump.carelevo.ble.commands.ExtendBolusCancelCommand
import app.aaps.pump.carelevo.ble.commands.ExtendBolusCommand
import app.aaps.pump.carelevo.ble.commands.ImmediateBolusCommand
import app.aaps.pump.carelevo.ble.commands.InfusionInfoCommand
import app.aaps.pump.carelevo.ble.commands.InfusionThresholdCommand
import app.aaps.pump.carelevo.ble.commands.MacAddressCommand
import app.aaps.pump.carelevo.ble.commands.MacAddressResponse
import app.aaps.pump.carelevo.ble.commands.NeedleAckCommand
import app.aaps.pump.carelevo.ble.commands.NeedleStatusCommand
import app.aaps.pump.carelevo.ble.commands.NoticeThresholdCommand
import app.aaps.pump.carelevo.ble.commands.PatchDiscardCommand
import app.aaps.pump.carelevo.ble.commands.PatchInfoCommand
import app.aaps.pump.carelevo.ble.commands.PumpResumeCommand
import app.aaps.pump.carelevo.ble.commands.PumpStopCommand
import app.aaps.pump.carelevo.ble.commands.SafetyCheckCommand
import app.aaps.pump.carelevo.ble.commands.SetTimeCommand
import app.aaps.pump.carelevo.ble.commands.SetTimeForPatchInfoCommand
import app.aaps.pump.carelevo.ble.commands.TempBasalCancelCommand
import app.aaps.pump.carelevo.ble.commands.TempBasalCommand
import app.aaps.pump.carelevo.ble.commands.ThresholdSetupCommand
import com.google.common.truth.Truth.assertThat
import org.joda.time.DateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.experimental.xor

/**
 * Wire-compatibility tests for [CarelevoPumpEmulator], driven end-to-end through the **production**
 * codec: every request is built with the real command's `encode()` and every response is parsed with
 * the real command's `decode(...)`. Nothing here hand-asserts an invented byte array, so a passing
 * test means the emulator is byte-for-byte compatible with what `BleClientImpl` actually writes and
 * reads — not merely self-consistent.
 *
 * The three [app.aaps.pump.carelevo.ble.BleClient] shapes are exercised the way the client drives them:
 * - [app.aaps.pump.carelevo.ble.BleCommand]      → `decode(frames.single())`
 * - [app.aaps.pump.carelevo.ble.BleMultiCommand] → `decode(frames.associateBy { it[0] })` (keyed by opcode)
 * - [app.aaps.pump.carelevo.ble.BleStreamCommand]→ decode each frame, terminate on `isTerminal(decoded)`
 *
 * Pure JVM: the emulator takes a nullable logger and touches no Android API, so no Robolectric.
 */
class CarelevoPumpEmulatorTest {

    private lateinit var state: CarelevoPumpState
    private lateinit var emulator: CarelevoPumpEmulator

    @BeforeEach
    fun setUp() {
        state = CarelevoPumpState()
        emulator = CarelevoPumpEmulator(state)
    }

    // ---- Pairing ---------------------------------------------------------------------------------

    @Test
    fun `mac address round-trip decodes the patch address and checksum`() {
        val cmd = MacAddressCommand(MAC_KEY.toByte())

        val frames = emulator.handle(cmd.encode())
        val decoded = cmd.decode(frames.single())

        assertThat(frames.single()[0]).isEqualTo(MacAddressCommand.RESPONSE_OPCODE)
        assertThat(decoded.macAddress).isEqualTo("94B2161D2F6D")
        assertThat(decoded.checkSum).isEqualTo("5A")
        // The key must be retained — 0x4B verifies the app's fold against it.
        assertThat(state.lastMacKey).isEqualTo(MAC_KEY)
    }

    @Test
    fun `app auth accepts the checksum fold the session computes`() {
        val macResponse = readMac(MAC_KEY)
        val cmd = AppAuthCommand(sessionFold(macResponse, MAC_KEY))

        val frames = emulator.handle(cmd.encode())
        val decoded = cmd.decode(frames.single())

        assertThat(decoded.resultCode).isEqualTo(0)
    }

    @Test
    fun `app auth rejects a wrong checksum`() {
        val macResponse = readMac(MAC_KEY)
        // One bit off the fold the patch expects — a real patch rejects this, so the emulator must too.
        val cmd = AppAuthCommand((sessionFold(macResponse, MAC_KEY) + 1) and 0xFF)

        val frames = emulator.handle(cmd.encode())
        val decoded = cmd.decode(frames.single())

        assertThat(decoded.resultCode).isNotEqualTo(0)
    }

    @Test
    fun `app auth before the mac read fails`() {
        // No 0x3B first, so the patch has no key to verify against.
        val cmd = AppAuthCommand(0x42)

        val decoded = cmd.decode(emulator.handle(cmd.encode()).single())

        assertThat(decoded.resultCode).isNotEqualTo(0)
    }

    @Test
    fun `app auth answers on 0xBB not 0x4B 0xBA or 0xAB`() {
        val macResponse = readMac(MAC_KEY)
        val cmd = AppAuthCommand(sessionFold(macResponse, MAC_KEY))

        val opcode = emulator.handle(cmd.encode()).single()[0]

        assertThat(opcode).isEqualTo(0xBB.toByte())
        assertThat(opcode).isNotEqualTo(0x4B.toByte())
        assertThat(opcode).isNotEqualTo(0xBA.toByte())
        assertThat(opcode).isNotEqualTo(0xAB.toByte())
        // ...and the production decoder agrees, since it requires 0xBB at byte 0.
        assertThat(cmd.decode(emulator.handle(cmd.encode()).single()).resultCode).isEqualTo(0)
    }

    @Test
    fun `app auth result code override wins over a correct fold`() {
        val macResponse = readMac(MAC_KEY)
        state.resultCodeOverrides[CarelevoPumpEmulator.OP_APP_AUTH] = 2
        val cmd = AppAuthCommand(sessionFold(macResponse, MAC_KEY))

        val decoded = cmd.decode(emulator.handle(cmd.encode()).single())

        assertThat(decoded.resultCode).isEqualTo(2)
    }

    // ---- The swapped needle pair -----------------------------------------------------------------

    @Test
    fun `needle ack 0x19 answers on 0x7A not 0x79`() {
        val cmd = NeedleAckCommand(isSuccess = true)

        val frames = emulator.handle(cmd.encode())

        // A "+0x60" emulator would answer 0x79 here and deadlock the client.
        assertThat(frames.single()[0]).isEqualTo(0x7A.toByte())
        assertThat(frames.single()[0]).isNotEqualTo(0x79.toByte())
        assertThat(cmd.decode(frames.single()).resultCode).isEqualTo(0)
        assertThat(state.needleInserted).isTrue()
    }

    @Test
    fun `needle status 0x1A answers on 0x79 not 0x7A`() {
        val cmd = NeedleStatusCommand()

        val frames = emulator.handle(cmd.encode())

        assertThat(frames.single()[0]).isEqualTo(0x79.toByte())
        assertThat(frames.single()[0]).isNotEqualTo(0x7A.toByte())
        assertThat(cmd.decode(frames.single()).resultCode).isEqualTo(0)
    }

    @Test
    fun `needle ack with the failure flag leaves the needle uninserted`() {
        val cmd = NeedleAckCommand(isSuccess = false)

        cmd.decode(emulator.handle(cmd.encode()).single())

        assertThat(state.needleInserted).isFalse()
    }

    // ---- Patch info ------------------------------------------------------------------------------

    @Test
    fun `patch info answers with exactly the two report frames 0x93 and 0x94`() {
        val cmd = PatchInfoCommand()

        val frames = emulator.handle(cmd.encode())

        assertThat(frames.map { it[0] }).containsExactly(0x93.toByte(), 0x94.toByte())
        assertThat(frames.map { it[0] }.toSet()).isEqualTo(cmd.expectedResponseOpcodes)
    }

    @Test
    fun `patch info round-trip decodes serial firmware and the decimal-concat model name`() {
        val cmd = PatchInfoCommand()

        val decoded = cmd.decode(emulator.handle(cmd.encode()).associateBy { it[0] })

        assertThat(decoded.serialResultCode).isEqualTo(0)
        assertThat(decoded.serialNumber).isEqualTo("CL24000000001")
        assertThat(decoded.detailResultCode).isEqualTo(0)
        assertThat(decoded.firmwareVersion).isEqualTo("1.10")
        // The quirk: model bytes are concatenated DECIMAL values, not ASCII — [1,2,0,0,0] -> "12000".
        assertThat(decoded.modelName).isEqualTo("12000")
    }

    @Test
    fun `patch info model name follows the state model bytes`() {
        state.modelBytes = byteArrayOf(2, 15, 0, 3, 0)
        val cmd = PatchInfoCommand()

        val decoded = cmd.decode(emulator.handle(cmd.encode()).associateBy { it[0] })

        assertThat(decoded.modelName).isEqualTo("215030")
    }

    // ---- SetTime split ---------------------------------------------------------------------------

    @Test
    fun `set time with subId 0 answers with the patch info pair`() {
        val cmd = SetTimeForPatchInfoCommand(subId = 0, volume = 200, aidMode = 0, dateTime = DATE_TIME)

        val frames = emulator.handle(cmd.encode())
        val decoded = cmd.decode(frames.associateBy { it[0] })

        // The activation path waits for 0x93+0x94; a 0x71 here would hang it.
        assertThat(frames.map { it[0] }).containsExactly(0x93.toByte(), 0x94.toByte())
        assertThat(decoded.serialNumber).isEqualTo("CL24000000001")
    }

    @Test
    fun `set time with a non-zero subId answers on 0x71`() {
        val cmd = SetTimeCommand(subId = 1, volume = 200, aidMode = 0, dateTime = DATE_TIME)

        val frames = emulator.handle(cmd.encode())
        val decoded = cmd.decode(frames.single())

        assertThat(frames.single()[0]).isEqualTo(SetTimeCommand.RESPONSE_OPCODE)
        assertThat(decoded.resultCode).isEqualTo(0)
    }

    @Test
    fun `set time frames differ only in the subId byte`() {
        val forPatchInfo = SetTimeForPatchInfoCommand(subId = 0, volume = 200, aidMode = 0, dateTime = DATE_TIME).encode()
        val plain = SetTimeCommand(subId = 1, volume = 200, aidMode = 0, dateTime = DATE_TIME).encode()

        // Byte-identical 11-byte frames on one opcode — only byte 1 tells the two commands apart,
        // which is exactly why the emulator has to branch on it.
        assertThat(forPatchInfo).hasLength(CarelevoPumpEmulator.SET_TIME_LENGTH)
        assertThat(plain).hasLength(CarelevoPumpEmulator.SET_TIME_LENGTH)
        assertThat(forPatchInfo[0]).isEqualTo(plain[0])
        assertThat(forPatchInfo[1]).isNotEqualTo(plain[1])
        assertThat(forPatchInfo.copyOfRange(2, forPatchInfo.size))
            .isEqualTo(plain.copyOfRange(2, plain.size))
    }

    @Test
    fun `set time records the subId and resets the running minutes`() {
        state.runningMinutes = 500
        val cmd = SetTimeCommand(subId = 1, volume = 150, aidMode = 0, dateTime = DATE_TIME)

        cmd.decode(emulator.handle(cmd.encode()).single())

        assertThat(state.subId).isEqualTo(1)
        assertThat(state.runningMinutes).isEqualTo(0)
    }

    // ---- Safety check stream ---------------------------------------------------------------------

    @Test
    fun `safety check streams progress frames then a terminal frame`() {
        state.safetyCheckProgressFrames = 3
        val cmd = SafetyCheckCommand()

        val decodedFrames = emulator.handle(cmd.encode()).map { cmd.decode(it) }

        assertThat(decodedFrames).hasSize(4)
        assertThat(decodedFrames.dropLast(1).map { cmd.isTerminal(it) }).containsExactly(false, false, false)
        assertThat(decodedFrames.last().resultCode).isEqualTo(SafetyCheckCommand.RESULT_SUCCESS)
        assertThat(cmd.isTerminal(decodedFrames.last())).isTrue()
    }

    @Test
    fun `safety check progress frames carry a progress result code`() {
        val cmd = SafetyCheckCommand()

        val decodedFrames = emulator.handle(cmd.encode()).map { cmd.decode(it) }

        // Progress is signalled purely by the result byte being REP_REQUEST/REP_REQUEST1.
        assertThat(decodedFrames.dropLast(1).map { it.resultCode })
            .containsExactly(SafetyCheckCommand.REP_REQUEST, SafetyCheckCommand.REP_REQUEST)
    }

    @Test
    fun `safety check never emits a 5-byte frame`() {
        state.safetyCheckProgressFrames = 4
        val cmd = SafetyCheckCommand()

        val frames = emulator.handle(cmd.encode())

        // SafetyCheckCommand.decode reads index 5 whenever size > 4, so a 5-byte frame would throw
        // ArrayIndexOutOfBounds in the real driver instead of failing cleanly.
        assertThat(frames.map { it.size }).doesNotContain(5)
        assertThat(frames.map { it.size }.all { it == 4 || it >= 6 }).isTrue()
        assertThat(frames.map { it[0] }.toSet()).containsExactly(SafetyCheckCommand.RESPONSE_OPCODE)
    }

    @Test
    fun `safety check frames decode the priming volume and duration`() {
        state.safetyCheckVolume = 250
        state.safetyCheckDurationSeconds = 185
        val cmd = SafetyCheckCommand()

        val decoded = cmd.decode(emulator.handle(cmd.encode()).last())

        assertThat(decoded.insulinVolume).isEqualTo(250)
        assertThat(decoded.durationSeconds).isEqualTo(185)
    }

    @Test
    fun `safety check success moves the patch to RUNNING`() {
        state.pumpStateRaw = CarelevoPumpEmulator.PUMP_STATE_READY
        val cmd = SafetyCheckCommand()

        cmd.decode(emulator.handle(cmd.encode()).last())

        assertThat(state.pumpStateRaw).isEqualTo(CarelevoPumpEmulator.PUMP_STATE_RUNNING)
    }

    @Test
    fun `safety check error terminates the stream and leaves the pump state alone`() {
        state.pumpStateRaw = CarelevoPumpEmulator.PUMP_STATE_READY
        state.safetyCheckResult = 11
        val cmd = SafetyCheckCommand()

        val decoded = cmd.decode(emulator.handle(cmd.encode()).last())

        // An error code is terminal too — the stream must complete rather than time out.
        assertThat(cmd.isTerminal(decoded)).isTrue()
        assertThat(decoded.resultCode).isEqualTo(11)
        assertThat(state.pumpStateRaw).isEqualTo(CarelevoPumpEmulator.PUMP_STATE_READY)
    }

    @Test
    fun `safety check with no progress frames emits only the terminal frame`() {
        state.safetyCheckProgressFrames = 0
        val cmd = SafetyCheckCommand()

        val frames = emulator.handle(cmd.encode())

        assertThat(frames).hasSize(1)
        assertThat(cmd.isTerminal(cmd.decode(frames.single()))).isTrue()
    }

    // ---- Immediate bolus -------------------------------------------------------------------------

    @Test
    fun `immediate bolus round-trip echoes the action id and decodes the remaining reservoir`() {
        val cmd = ImmediateBolusCommand(actionId = 42, volume = 2.5)

        val frames = emulator.handle(cmd.encode())
        val decoded = cmd.decode(frames.single())

        assertThat(frames.single()[0]).isEqualTo(ImmediateBolusCommand.RESPONSE_OPCODE)
        // Byte 1 is the correlation byte — the client silently drops a frame that does not match.
        assertThat(frames.single()[1]).isEqualTo(cmd.correlationByte)
        assertThat(decoded.actionId).isEqualTo(42)
        assertThat(decoded.resultCode).isEqualTo(0)
        assertThat(decoded.expectedCompletionSeconds).isEqualTo(50)
        assertThat(decoded.remainingReservoirUnits).isWithin(TOLERANCE).of(197.5)
    }

    @Test
    fun `immediate bolus echoes an action id above 127 unsigned`() {
        val cmd = ImmediateBolusCommand(actionId = 200, volume = 1.0)

        val decoded = cmd.decode(emulator.handle(cmd.encode()).single())

        assertThat(decoded.actionId).isEqualTo(200)
        assertThat(state.activeBolusActionId).isEqualTo(200)
    }

    @Test
    fun `immediate bolus decrements the reservoir and adds to the bolus total`() {
        state.insulinRemaining = 100.0
        state.infusedTotalBolus = 3.0
        val cmd = ImmediateBolusCommand(actionId = 7, volume = 4.25)

        cmd.decode(emulator.handle(cmd.encode()).single())

        assertThat(state.insulinRemaining).isWithin(TOLERANCE).of(95.75)
        assertThat(state.infusedTotalBolus).isWithin(TOLERANCE).of(7.25)
        assertThat(state.modeRaw).isEqualTo(CarelevoPumpEmulator.MODE_IMME_BOLUS)
        // The emulated bolus lands immediately, so the whole dose is already infused — a later cancel
        // reports this, rather than claiming 0 U for insulin the totals above have already counted.
        assertThat(state.bolusInfusedAmount).isWithin(TOLERANCE).of(4.25)
    }

    @Test
    fun `immediate bolus failure keeps the reservoir and still echoes the action id`() {
        state.resultCodeOverrides[CarelevoPumpEmulator.OP_IMMEDIATE_BOLUS] = 6
        val cmd = ImmediateBolusCommand(actionId = 99, volume = 3.0)

        val frames = emulator.handle(cmd.encode())
        val decoded = cmd.decode(frames.single())

        // Result code lives at index 2 — index 1 is the echoed actionId, not the result.
        assertThat(frames.single()[2]).isEqualTo(6.toByte())
        assertThat(decoded.resultCode).isEqualTo(6)
        assertThat(decoded.actionId).isEqualTo(99)
        assertThat(state.insulinRemaining).isWithin(TOLERANCE).of(200.0)
        assertThat(state.activeBolusActionId).isNull()
    }

    // ---- Infusion info ---------------------------------------------------------------------------

    @Test
    fun `infusion info frame is 20 bytes and echoes the inquiry type`() {
        val cmd = InfusionInfoCommand(inquiryType = 1)

        val frames = emulator.handle(cmd.encode())
        val decoded = cmd.decode(frames.single())

        // The decoder reads index 19, so anything shorter than 20 fails the require().
        assertThat(frames.single().size).isAtLeast(20)
        assertThat(frames.single()[0]).isEqualTo(InfusionInfoCommand.RESPONSE_OPCODE)
        assertThat(decoded.subId).isEqualTo(1)
    }

    @Test
    fun `infusion info round-trip decodes every field`() {
        state.runningMinutes = 125
        state.insulinRemaining = 187.65
        state.infusedTotalBasal = 12.34
        state.infusedTotalBolus = 5.5
        state.pumpStateRaw = CarelevoPumpEmulator.PUMP_STATE_RUNNING
        state.modeRaw = CarelevoPumpEmulator.MODE_BASAL
        val cmd = InfusionInfoCommand()

        val decoded = cmd.decode(emulator.handle(cmd.encode()).single())

        assertThat(decoded.subId).isEqualTo(0)
        assertThat(decoded.runningMinutes).isEqualTo(125)
        assertThat(decoded.insulinRemaining).isWithin(TOLERANCE).of(187.65)
        assertThat(decoded.infusedTotalBasalAmount).isWithin(TOLERANCE).of(12.34)
        assertThat(decoded.infusedTotalBolusAmount).isWithin(TOLERANCE).of(5.5)
        assertThat(decoded.pumpStateRaw).isEqualTo(2)
        assertThat(decoded.modeRaw).isEqualTo(1)
        assertThat(decoded.currentInfusedProgramVolume).isWithin(TOLERANCE).of(5.5)
        // realInfusedTime is seconds: 125 min -> 7500 s.
        assertThat(decoded.realInfusedTime).isEqualTo(7500)
    }

    // ---- Basal program ---------------------------------------------------------------------------

    @Test
    fun `basal program acks each sequence individually and commits only on seq 2`() {
        val seq0 = BasalProgramCommand(isUpdate = false, seqNo = 0, segmentSpeeds = listOf(0.5, 0.75))
        val seq1 = BasalProgramCommand(isUpdate = false, seqNo = 1, segmentSpeeds = listOf(1.25, 1.5))
        val seq2 = BasalProgramCommand(isUpdate = false, seqNo = 2, segmentSpeeds = listOf(2.0, 2.5))

        assertThat(seq0.decode(emulator.handle(seq0.encode()).single()).resultCode).isEqualTo(0)
        assertThat(state.basalProgramCommitted).isFalse()

        assertThat(seq1.decode(emulator.handle(seq1.encode()).single()).resultCode).isEqualTo(0)
        assertThat(state.basalProgramCommitted).isFalse()

        assertThat(seq2.decode(emulator.handle(seq2.encode()).single()).resultCode).isEqualTo(0)
        assertThat(state.basalProgramCommitted).isTrue()
    }

    @Test
    fun `basal program returns the segments in sequence order`() {
        listOf(
            BasalProgramCommand(isUpdate = false, seqNo = 2, segmentSpeeds = listOf(2.0, 2.5)),
            BasalProgramCommand(isUpdate = false, seqNo = 0, segmentSpeeds = listOf(0.5, 0.75)),
            BasalProgramCommand(isUpdate = false, seqNo = 1, segmentSpeeds = listOf(1.25, 1.5))
        ).forEach { cmd -> cmd.decode(emulator.handle(cmd.encode()).single()) }

        // Written out of order; the program still reads back in segment order.
        assertThat(state.basalProgram).containsExactly(0.5, 0.75, 1.25, 1.5, 2.0, 2.5).inOrder()
    }

    @Test
    fun `basal program set uses 0x13 to 0x73 and update uses 0x21 to 0x81`() {
        val set = BasalProgramCommand(isUpdate = false, seqNo = 0, segmentSpeeds = listOf(1.0))
        val update = BasalProgramCommand(isUpdate = true, seqNo = 0, segmentSpeeds = listOf(1.0))

        val setFrame = emulator.handle(set.encode()).single()
        val updateFrame = emulator.handle(update.encode()).single()

        assertThat(set.encode()[0]).isEqualTo(BasalProgramCommand.SET_REQUEST_OPCODE)
        assertThat(setFrame[0]).isEqualTo(BasalProgramCommand.SET_RESPONSE_OPCODE)
        assertThat(update.encode()[0]).isEqualTo(BasalProgramCommand.UPDATE_REQUEST_OPCODE)
        assertThat(updateFrame[0]).isEqualTo(BasalProgramCommand.UPDATE_RESPONSE_OPCODE)
        assertThat(set.decode(setFrame).resultCode).isEqualTo(0)
        assertThat(update.decode(updateFrame).resultCode).isEqualTo(0)
    }

    @Test
    fun `basal program failure does not bank the segments`() {
        state.resultCodeOverrides[BasalProgramCommand.SET_REQUEST_OPCODE] = 9
        val cmd = BasalProgramCommand(isUpdate = false, seqNo = 2, segmentSpeeds = listOf(1.0, 2.0))

        val decoded = cmd.decode(emulator.handle(cmd.encode()).single())

        assertThat(decoded.resultCode).isEqualTo(9)
        assertThat(state.basalProgramCommitted).isFalse()
        assertThat(state.basalProgram).isEmpty()
    }

    // ---- Index-shifted result codes ---------------------------------------------------------------

    @Test
    fun `infusion threshold carries its result at index 2 behind the echoed type`() {
        state.resultCodeOverrides[CarelevoPumpEmulator.OP_INFUSION_THRESHOLD] = 3
        val cmd = InfusionThresholdCommand(isMaxVolume = true, value = 12.5)

        val frames = emulator.handle(cmd.encode())
        val decoded = cmd.decode(frames.single())

        // Byte 1 echoes the type flag, so the result sits at index 2 — not the usual index 1.
        assertThat(frames.single()[1]).isEqualTo(0x01.toByte())
        assertThat(frames.single()[2]).isEqualTo(3.toByte())
        assertThat(decoded.type).isEqualTo(1)
        assertThat(decoded.resultCode).isEqualTo(3)
    }

    @Test
    fun `infusion threshold max volume round-trip stores the max bolus dose`() {
        val cmd = InfusionThresholdCommand(isMaxVolume = true, value = 12.5)

        val decoded = cmd.decode(emulator.handle(cmd.encode()).single())

        assertThat(decoded.type).isEqualTo(1)
        assertThat(decoded.resultCode).isEqualTo(0)
        assertThat(state.maxBolusDose).isWithin(TOLERANCE).of(12.5)
    }

    @Test
    fun `infusion threshold max speed round-trip stores the max basal speed`() {
        val cmd = InfusionThresholdCommand(isMaxVolume = false, value = 3.75)

        val decoded = cmd.decode(emulator.handle(cmd.encode()).single())

        assertThat(decoded.type).isEqualTo(0)
        assertThat(state.maxBasalSpeed).isWithin(TOLERANCE).of(3.75)
    }

    @Test
    fun `alarm clear carries its result at index 3 behind the echoed type and cause`() {
        state.resultCodeOverrides[CarelevoPumpEmulator.OP_ALARM_CLEAR] = 4
        val cmd = AlarmClearCommand(alarmType = 5, cause = 3)

        val frames = emulator.handle(cmd.encode())
        val decoded = cmd.decode(frames.single())

        assertThat(frames.single()[0]).isEqualTo(AlarmClearCommand.RESPONSE_OPCODE)
        assertThat(frames.single()[3]).isEqualTo(4.toByte())
        assertThat(decoded.subId).isEqualTo(5)
        assertThat(decoded.cause).isEqualTo(3)
        assertThat(decoded.resultCode).isEqualTo(4)
    }

    @Test
    fun `notice threshold echoes the type with no result code on the wire`() {
        val lowInsulin = NoticeThresholdCommand(thresholdType = NoticeThresholdCommand.TYPE_LOW_INSULIN, value = 35)
        val expiry = NoticeThresholdCommand(thresholdType = NoticeThresholdCommand.TYPE_EXPIRY, value = 48)

        val lowFrame = emulator.handle(lowInsulin.encode()).single()
        val expiryFrame = emulator.handle(expiry.encode()).single()

        // The 0x75 frame is [opcode][type] — arrival IS the success signal; resultCode is fabricated.
        assertThat(lowFrame).hasLength(2)
        assertThat(lowFrame[1]).isEqualTo(0x00.toByte())
        assertThat(expiryFrame[1]).isEqualTo(0x01.toByte())
        assertThat(lowInsulin.decode(lowFrame).thresholdType).isEqualTo(NoticeThresholdCommand.TYPE_LOW_INSULIN)
        assertThat(lowInsulin.decode(lowFrame).resultCode).isEqualTo(0)
        assertThat(expiry.decode(expiryFrame).thresholdType).isEqualTo(NoticeThresholdCommand.TYPE_EXPIRY)
        assertThat(state.lowInsulinNotice).isEqualTo(35)
        assertThat(state.expiryNotice).isEqualTo(48)
    }

    // ---- Remaining opcodes ------------------------------------------------------------------------

    @Test
    fun `buzz mode round-trip stores the buzzer flag`() {
        val on = BuzzModeCommand(use = true)
        assertThat(on.decode(emulator.handle(on.encode()).single()).resultCode).isEqualTo(0)
        assertThat(state.buzzUse).isTrue()

        val off = BuzzModeCommand(use = false)
        assertThat(off.decode(emulator.handle(off.encode()).single()).resultCode).isEqualTo(0)
        assertThat(state.buzzUse).isFalse()
    }

    @Test
    fun `threshold setup round-trip stores the whole activation bundle`() {
        val cmd = ThresholdSetupCommand(
            insulinRemainsThreshold = 30,
            expiryThreshold = 48,
            maxBasalSpeed = 10.5,
            maxBolusDose = 20.25,
            buzzUse = true
        )

        val decoded = cmd.decode(emulator.handle(cmd.encode()).single())

        assertThat(decoded.resultCode).isEqualTo(0)
        assertThat(state.insulinRemainsThreshold).isEqualTo(30)
        assertThat(state.expiryThreshold).isEqualTo(48)
        assertThat(state.maxBasalSpeed).isWithin(TOLERANCE).of(10.5)
        assertThat(state.maxBolusDose).isWithin(TOLERANCE).of(20.25)
        assertThat(state.buzzUse).isTrue()
    }

    @Test
    fun `additional priming round-trip counts the priming pulse`() {
        val cmd = AdditionalPrimingCommand()

        assertThat(cmd.decode(emulator.handle(cmd.encode()).single()).resultCode).isEqualTo(0)
        cmd.decode(emulator.handle(cmd.encode()).single())

        assertThat(state.primingCount).isEqualTo(2)
    }

    @Test
    fun `temp basal by unit round-trip starts the temp basal`() {
        val cmd = TempBasalCommand.byUnit(infusionUnit = 1.5, infusionHour = 2, infusionMin = 30)

        val decoded = cmd.decode(emulator.handle(cmd.encode()).single())

        assertThat(cmd.encode()).hasLength(6) // BY_UNIT carries the trailing 0x00
        assertThat(decoded.resultCode).isEqualTo(0)
        assertThat(state.tempBasalRunning).isTrue()
        assertThat(state.modeRaw).isEqualTo(CarelevoPumpEmulator.MODE_TEMP_BASAL)
    }

    @Test
    fun `temp basal by percent round-trip uses the same opcode pair`() {
        val cmd = TempBasalCommand.byPercent(infusionPercent = 150, infusionHour = 1, infusionMin = 0)

        val frames = emulator.handle(cmd.encode())
        val decoded = cmd.decode(frames.single())

        assertThat(cmd.encode()).hasLength(5) // BY_PERCENT has no trailing byte — the asymmetry
        assertThat(frames.single()[0]).isEqualTo(TempBasalCommand.RESPONSE_OPCODE)
        assertThat(decoded.resultCode).isEqualTo(0)
        assertThat(state.tempBasalRunning).isTrue()
    }

    @Test
    fun `temp basal cancel round-trip stops the temp basal`() {
        state.tempBasalRunning = true
        state.modeRaw = CarelevoPumpEmulator.MODE_TEMP_BASAL
        val cmd = TempBasalCancelCommand()

        val decoded = cmd.decode(emulator.handle(cmd.encode()).single())

        assertThat(decoded.resultCode).isEqualTo(0)
        assertThat(state.tempBasalRunning).isFalse()
        assertThat(state.modeRaw).isEqualTo(CarelevoPumpEmulator.MODE_BASAL)
    }

    @Test
    fun `extend bolus round-trip starts the extended bolus`() {
        val cmd = ExtendBolusCommand(immediateDose = 1.0, extendedSpeed = 0.5, hour = 1, min = 30)

        val decoded = cmd.decode(emulator.handle(cmd.encode()).single())

        assertThat(decoded.resultCode).isEqualTo(0)
        // The decoder recombines bytes 2..3 as `[2] * 60 + [3]` seconds, so a 1h30m request must come
        // back as 5400 s — encoded [90, 0], with the minutes in byte 2 rather than split again by 60.
        assertThat(decoded.expectedTimeSeconds).isEqualTo(5400)
        assertThat(state.extendedBolusRunning).isTrue()
        assertThat(state.modeRaw).isEqualTo(CarelevoPumpEmulator.MODE_EXTEND_BOLUS)
    }

    @Test
    fun `extend bolus cancel round-trip reports the infused amount`() {
        state.extendedBolusRunning = true
        state.extendedInfusedAmount = 3.25
        val cmd = ExtendBolusCancelCommand()

        val decoded = cmd.decode(emulator.handle(cmd.encode()).single())

        assertThat(decoded.resultCode).isEqualTo(0)
        assertThat(decoded.infusedAmount).isWithin(TOLERANCE).of(3.25)
        assertThat(state.extendedBolusRunning).isFalse()
        assertThat(state.modeRaw).isEqualTo(CarelevoPumpEmulator.MODE_BASAL)
    }

    @Test
    fun `bolus cancel round-trip reports the infused amount and clears the action id`() {
        state.activeBolusActionId = 42
        state.bolusInfusedAmount = 1.75
        val cmd = BolusCancelCommand()

        val decoded = cmd.decode(emulator.handle(cmd.encode()).single())

        assertThat(decoded.resultCode).isEqualTo(0)
        assertThat(decoded.infusedAmount).isWithin(TOLERANCE).of(1.75)
        assertThat(state.activeBolusActionId).isNull()
        assertThat(state.modeRaw).isEqualTo(CarelevoPumpEmulator.MODE_BASAL)
    }

    @Test
    fun `pump stop round-trip stops the patch and records the subId`() {
        val cmd = PumpStopCommand(durationMinutes = 90, subId = 1)

        val decoded = cmd.decode(emulator.handle(cmd.encode()).single())

        assertThat(decoded.resultCode).isEqualTo(0)
        assertThat(state.stopped).isTrue()
        assertThat(state.subId).isEqualTo(1)
    }

    @Test
    fun `pump resume round-trip echoes the mode and defaults the optional subId`() {
        state.stopped = true
        val cmd = PumpResumeCommand(mode = 1, subId = 1)

        val frames = emulator.handle(cmd.encode())
        val decoded = cmd.decode(frames.single())

        // The 4th subId byte is omitted; the decoder must default it rather than fail.
        assertThat(frames.single()).hasLength(3)
        assertThat(decoded.resultCode).isEqualTo(0)
        assertThat(decoded.mode).isEqualTo(1)
        assertThat(decoded.subId).isEqualTo(0)
        assertThat(state.stopped).isFalse()
        assertThat(state.modeRaw).isEqualTo(CarelevoPumpEmulator.MODE_BASAL)
    }

    @Test
    fun `patch discard round-trip discards the patch and returns it to READY`() {
        val cmd = PatchDiscardCommand()

        val decoded = cmd.decode(emulator.handle(cmd.encode()).single())

        assertThat(decoded.resultCode).isEqualTo(0)
        assertThat(state.discarded).isTrue()
        assertThat(state.pumpStateRaw).isEqualTo(CarelevoPumpEmulator.PUMP_STATE_READY)
    }

    @Test
    fun `alert alarm set round-trip stores the alarm mode`() {
        val cmd = AlertAlarmSetCommand(mode = 3)

        val frames = emulator.handle(cmd.encode())
        val decoded = cmd.decode(frames.single())

        assertThat(frames.single()[0]).isEqualTo(AlertAlarmSetCommand.RESPONSE_OPCODE)
        assertThat(decoded.resultCode).isEqualTo(0)
        assertThat(state.alertAlarmMode).isEqualTo(3)
    }

    // ---- Fault injection --------------------------------------------------------------------------

    @Test
    fun `silent opcodes make the patch stay quiet`() {
        state.silentOpcodes += InfusionInfoCommand.REQUEST_OPCODE
        val silent = InfusionInfoCommand()
        val loud = PatchDiscardCommand()

        assertThat(emulator.handle(silent.encode())).isEmpty()
        // ...and only that opcode goes quiet.
        assertThat(emulator.handle(loud.encode())).hasSize(1)
    }

    @Test
    fun `result code overrides come back through the real decoder`() {
        state.resultCodeOverrides[CarelevoPumpEmulator.OP_PUMP_STOP] = 12
        state.resultCodeOverrides[CarelevoPumpEmulator.OP_PATCH_DISCARD] = 7
        val stop = PumpStopCommand(durationMinutes = 30, subId = 0)
        val discard = PatchDiscardCommand()

        assertThat(stop.decode(emulator.handle(stop.encode()).single()).resultCode).isEqualTo(12)
        assertThat(discard.decode(emulator.handle(discard.encode()).single()).resultCode).isEqualTo(7)
        // A non-success result must not apply the state mutation.
        assertThat(state.stopped).isFalse()
        assertThat(state.discarded).isFalse()
    }

    @Test
    fun `silent opcode wins over a result code override`() {
        state.silentOpcodes += CarelevoPumpEmulator.OP_PATCH_DISCARD
        state.resultCodeOverrides[CarelevoPumpEmulator.OP_PATCH_DISCARD] = 7

        assertThat(emulator.handle(PatchDiscardCommand().encode())).isEmpty()
    }

    // ---- Malformed input --------------------------------------------------------------------------

    @Test
    fun `empty request is ignored`() {
        assertThat(emulator.handle(ByteArray(0))).isEmpty()
    }

    @Test
    fun `unknown opcode is ignored`() {
        assertThat(emulator.handle(byteArrayOf(0x7F, 0x01, 0x02))).isEmpty()
    }

    // ---- Helpers ----------------------------------------------------------------------------------

    /** Runs the real 0x3B round-trip and returns the decoded response. */
    private fun readMac(key: Int): MacAddressResponse {
        val cmd = MacAddressCommand(key.toByte())
        return cmd.decode(emulator.handle(cmd.encode()).single())
    }

    /**
     * The app-side handshake fold, exactly as `CarelevoBleSession.runPairing` computes it:
     * `(macResponse.macAddress + macResponse.checkSum).convertHexToByteArray().checkSumV2(key)`.
     *
     * `convertHexToByteArray`/`checkSumV2` are `internal` to `:pump:carelevo`, and Kotlin only grants
     * internal visibility to a module's own test source set — so this module cannot call them and the
     * hex-parse + XOR fold is replicated here instead. Kept deliberately literal (parse the decoded
     * hex strings, fold from `key.toByte()`) so it stays a faithful stand-in for the session's code.
     */
    private fun sessionFold(macResponse: MacAddressResponse, key: Int): Int =
        (macResponse.macAddress + macResponse.checkSum)
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .fold(key.toByte()) { acc, b -> acc xor b }
            .toUByte()
            .toInt()

    private companion object {

        /** Above 127 on purpose — proves the key survives the Int→Byte→unsigned round-trip. */
        const val MAC_KEY = 0xC3

        val DATE_TIME: DateTime = DateTime(2026, 7, 16, 14, 30, 45)

        const val TOLERANCE = 1e-9
    }
}
