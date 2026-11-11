package app.aaps.pump.danar.emulator

import app.aaps.core.interfaces.pump.emulator.EmulatorSpeed
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.comm.RecordTypes
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Command-level tests for the DanaR RFCOMM emulator, the JVM counterpart to the Dana RS
 * emulator's `PumpEmulatorTest`.
 *
 * On a device the DanaR scenario classes drive `DanaRPumpEmulator` through the drivers. These call `processCommand`
 * directly — no device, no execution service — and assert on the mutable [DanaRPumpState] or the
 * response bytes. Command values mirror the wire format the DanaR `Msg*` packets produce (amounts in
 * hundredths, big-endian).
 */
class DanaRPumpEmulatorTest {

    private fun emulator(variant: DanaRVariant = DanaRVariant.DANA_R_V2) =
        DanaRPumpEmulator(DanaRPumpState(variant))

    // --- identity / init ---

    @Test
    fun checkValueReturnsVariantHardwareModel() {
        // 0xF0F1 CHECK_VALUE: the app detects the variant from [hwModel, protocol, productCode].
        val emulator = emulator(DanaRVariant.DANA_R_KOREAN)
        val response = emulator.processCommand(0xF0F1, ByteArray(0))
        assertThat(response[0].toInt() and 0xFF).isEqualTo(0x01) // Korean hwModel
        assertThat(response[1].toInt() and 0xFF).isEqualTo(0x00) // protocol
    }

    @Test
    fun koreanInitTimeIsPaddedToTriggerKoreanDetection() {
        // 0x0301 INIT_CONN_STATUS_TIME: the app decides "Korean" from the payload length. Korean is
        // padded with four version bytes (>= 10); the others carry one (7).
        val korean = emulator(DanaRVariant.DANA_R_KOREAN).processCommand(0x0301, ByteArray(0))
        val standard = emulator(DanaRVariant.DANA_R).processCommand(0x0301, ByteArray(0))
        assertThat(korean.size).isAtLeast(10)
        assertThat(standard.size).isLessThan(korean.size)
    }

    // --- bolus ---

    @Test
    fun bolusStartUpdatesTotalsAndReservoir() {
        val state = DanaRPumpState(DanaRVariant.DANA_R_V2).apply {
            dailyTotalUnits = 5.0
            reservoirRemainingUnits = 150.0
        }
        val emulator = DanaRPumpEmulator(state).apply { speed = EmulatorSpeed.FAST }
        val ended = CountDownLatch(1)
        emulator.onAdditionalResponse = { command, _ -> if (command == 0x0101) ended.countDown() }

        // 0x0102 SET_STEP_BOLUS_START: amount in hundredths, big-endian. 1.50 U = 150 = 0x00 0x96.
        val response = emulator.processCommand(0x0102, byteArrayOf(0x00, 0x96.toByte()))

        assertThat(response[0].toInt() and 0xFF).isEqualTo(0x02) // documented success code
        // The pump gives the bolus step by step and ends it with MsgBolusStop (0x0101), the message the driver waits for.
        assertThat(ended.await(10, TimeUnit.SECONDS)).isTrue()
        // As a real DanaRv2, the pump has the bolus in its status about 2 s after the end.
        val loggedBy = System.currentTimeMillis() + 5_000
        while (state.lastBolusAmount == 0.0 && System.currentTimeMillis() < loggedBy) Thread.sleep(50)
        assertThat(state.lastBolusAmount).isWithin(0.001).of(1.50)
        assertThat(state.dailyTotalUnits).isWithin(0.001).of(6.50)
        assertThat(state.reservoirRemainingUnits).isWithin(0.001).of(148.50)
        assertThat(state.givenBoluses).containsExactly(1.50)
    }

    // --- temp basal ---

    @Test
    fun setTempBasalStartUpdatesState() {
        val state = DanaRPumpState(DanaRVariant.DANA_R_V2)
        val emulator = DanaRPumpEmulator(state)

        // 0x0401: percent, then duration in hours.
        emulator.processCommand(0x0401, byteArrayOf(150.toByte(), 2))

        assertThat(state.isTempBasalRunning).isTrue()
        assertThat(state.tempBasalPercent).isEqualTo(150)
        assertThat(state.tempBasalDurationMinutes).isEqualTo(120)
    }

    @Test
    fun setTempBasalStopClearsState() {
        val state = DanaRPumpState(DanaRVariant.DANA_R_V2).apply {
            isTempBasalRunning = true
            tempBasalPercent = 150
        }
        val emulator = DanaRPumpEmulator(state)

        emulator.processCommand(0x0403, ByteArray(0))

        assertThat(state.isTempBasalRunning).isFalse()
        assertThat(state.tempBasalPercent).isEqualTo(0)
    }

    // --- extended bolus ---

    @Test
    fun setExtendedBolusStartUpdatesState() {
        val state = DanaRPumpState(DanaRVariant.DANA_R_V2)
        val emulator = DanaRPumpEmulator(state)

        // 0x0407: amount in hundredths (BE), then duration in half-hours. 1.00 U = 100 = 0x00 0x64.
        emulator.processCommand(0x0407, byteArrayOf(0x00, 0x64, 3))

        assertThat(state.isExtendedBolusRunning).isTrue()
        assertThat(state.extendedBolusAmount).isWithin(0.001).of(1.0)
        assertThat(state.extendedBolusDurationHalfHours).isEqualTo(3)
    }

    @Test
    fun setExtendedBolusStopClearsState() {
        val state = DanaRPumpState(DanaRVariant.DANA_R_V2).apply {
            isExtendedBolusRunning = true
            extendedBolusAmount = 1.0
        }
        val emulator = DanaRPumpEmulator(state)

        emulator.processCommand(0x0406, ByteArray(0))

        assertThat(state.isExtendedBolusRunning).isFalse()
        assertThat(state.extendedBolusAmount).isEqualTo(0.0)
    }

    @Test
    fun extendedBolusStatusGivesTheSecondsItRuns() {
        val state = DanaRPumpState(DanaRVariant.DANA_R_V2)
        val emulator = DanaRPumpEmulator(state)
        emulator.processCommand(0x0407, byteArrayOf(0x00, 0x64, 2))
        // As if it had started 90 s ago, by the pump clock.
        state.extendedBolusStartTime -= 90_000

        // 0x0207: [running][half hours][amount, 2 bytes][seconds it runs, 3 bytes, low byte first]
        val response = emulator.processCommand(0x0207, ByteArray(0))

        val seconds = (response[4].toInt() and 0xFF) or (response[5].toInt() and 0xFF shl 8) or (response[6].toInt() and 0xFF shl 16)
        assertThat(seconds).isIn(90..91)
    }

    @Test
    fun dualBolusOnThePumpLogsBothPartsAndItsEnd() {
        val state = DanaRPumpState(DanaRVariant.DANA_R_V2).apply { reservoirRemainingUnits = 100.0 }
        val emulator = DanaRPumpEmulator(state)

        assertThat(emulator.dualBolusOnPump(now = 0.5, extended = 1.0, halfHours = 1)).isTrue()
        assertThat(emulator.dualBolusOnPump(now = 0.5, extended = 1.0, halfHours = 1)).isFalse()
        emulator.stopExtendedBolusOnPump()

        val codes = state.historyStore.getEventsAfter(0).map { it.code }
        assertThat(codes).containsExactly(
            DanaPump.HistoryEntry.DUAL_BOLUS.value, DanaPump.HistoryEntry.DUAL_EXTENDED_START.value, DanaPump.HistoryEntry.DUAL_EXTENDED_STOP.value
        ).inOrder()
        assertThat(state.givenBoluses).containsExactly(0.5)
        assertThat(state.reservoirRemainingUnits).isWithin(0.001).of(99.5)
        assertThat(state.isExtendedBolusRunning).isFalse()
    }

    @Test
    fun aDailyTotalHasItsOwnLayout() {
        val state = DanaRPumpState(DanaRVariant.DANA_R_V2)
        val emulator = DanaRPumpEmulator(state)
        state.reviewHistoryStore.addEvent(RecordTypes.RECORD_TYPE_DAILY.toInt(), System.currentTimeMillis(), 1234, 567)

        // 0x3102 daily totals: [code][year][month][day][basal, 2 bytes][bolus, 2 bytes], in hundredths.
        val response = emulator.processCommand(0x3102, ByteArray(0))

        assertThat((response[4].toInt() and 0xFF shl 8) or (response[5].toInt() and 0xFF)).isEqualTo(1234)
        assertThat((response[6].toInt() and 0xFF shl 8) or (response[7].toInt() and 0xFF)).isEqualTo(567)
    }

    // --- settings: write then read back ---

    @Test
    fun setUserOptionsUpdatesState() {
        val state = DanaRPumpState(DanaRVariant.DANA_R_V2)
        val emulator = DanaRPumpEmulator(state)

        // 0x330B: the 33 bytes of the 0x320B answer with the changes, at the same places, but the low reservoir in
        // byte 27 (a real DanaRv2 reports it in byte 32 and takes it from byte 27).
        val params = ByteArray(33)
        params[0] = 1     // 12h
        params[2] = 2     // beep+alarm
        params[3] = 30    // lcd on time
        params[8] = 1     // mmol/L
        params[9] = 22    // shutdown hour
        params[27] = 15   // low reservoir rate
        params[32] = 20   // the old value, as read
        emulator.processCommand(0x330B, params)

        assertThat(state.timeDisplayType24).isFalse()
        assertThat(state.beepAndAlarm).isEqualTo(2)
        assertThat(state.lcdOnTimeSec).isEqualTo(30)
        assertThat(state.glucoseUnit).isEqualTo(1)
        assertThat(state.shutdownHour).isEqualTo(22)
        assertThat(state.lowReservoirRate).isEqualTo(15)
    }

    @Test
    fun settingUserOptionsReflectsState() {
        val state = DanaRPumpState(DanaRVariant.DANA_R_V2).apply {
            lcdOnTimeSec = 45
            glucoseUnit = 1
            beepAndAlarm = 3
        }
        val emulator = DanaRPumpEmulator(state)

        // 0x320B read: data[2]=beepAndAlarm, data[3]=lcdOnTimeSec, data[8]=glucoseUnit.
        val response = emulator.processCommand(0x320B, ByteArray(0))

        assertThat(response.size).isAtLeast(33)
        assertThat(response[2].toInt() and 0xFF).isEqualTo(3)
        assertThat(response[3].toInt() and 0xFF).isEqualTo(45)
        assertThat(response[8].toInt() and 0xFF).isEqualTo(1)
    }

    @Test
    fun settingMaxValuesReflectsState() {
        val state = DanaRPumpState(DanaRVariant.DANA_R_V2).apply { maxBolus = 10.0 }
        val emulator = DanaRPumpEmulator(state)

        // 0x3205: maxBolus in hundredths, big-endian. 10.0 U = 1000 = 0x03 0xE8.
        val response = emulator.processCommand(0x3205, ByteArray(0))

        val maxBolusInt = (response[0].toInt() and 0xFF shl 8) or (response[1].toInt() and 0xFF)
        assertThat(maxBolusInt).isEqualTo(1000)
    }

    // --- basal profile ---

    @Test
    fun setSingleBasalProfileUpdatesActiveProfileRates() {
        val state = DanaRPumpState(DanaRVariant.DANA_R_V2)
        val emulator = DanaRPumpEmulator(state)

        // 0x3302: 24 hourly rates, each in hundredths, big-endian. Hour 0 = 1.50 U/h = 0x00 0x96.
        val params = ByteArray(48)
        params[0] = 0x00
        params[1] = 0x96.toByte()
        emulator.processCommand(0x3302, params)

        assertThat(state.basalProfiles[state.activeProfile][0]).isWithin(0.001).of(1.50)
    }

    /** 0x3306 starts with the profile number. A read back must give the rates that were set, not shifted by a byte. */
    @Test
    fun setBasalProfileStoresTheRatesAfterTheProfileNumber() {
        val state = DanaRPumpState(DanaRVariant.DANA_R)
        val emulator = DanaRPumpEmulator(state)
        // Profile 0, then 6 hours at 0.51 U/h (0x0033) and 18 hours at 1.02 U/h (0x0066), high byte first.
        val params = byteArrayOf(0) + (0 until 24).flatMap { hour -> listOf(0.toByte(), (if (hour < 6) 0x33 else 0x66).toByte()) }

        emulator.processCommand(0x3306, params)
        emulator.processCommand(0x330C, byteArrayOf(0))
        val read = emulator.processCommand(0x3202, ByteArray(0))

        val rates = (0 until 24).map { ((read[it * 2].toInt() and 0xFF shl 8) or (read[it * 2 + 1].toInt() and 0xFF)) / 100.0 }
        assertThat(rates).containsExactlyElementsIn((0 until 24).map { if (it < 6) 0.51 else 1.02 }).inOrder()
    }

    @Test
    fun setActivateBasalProfileUpdatesActiveProfile() {
        val state = DanaRPumpState(DanaRVariant.DANA_R_V2)
        val emulator = DanaRPumpEmulator(state)

        emulator.processCommand(0x330C, byteArrayOf(2))

        assertThat(state.activeProfile).isEqualTo(2)
    }

    // --- status read ---

    @Test
    fun statusTempBasalReflectsRunningTbr() {
        val state = DanaRPumpState(DanaRVariant.DANA_R_V2).apply {
            isTempBasalRunning = true
            tempBasalPercent = 120
            tempBasalDurationMinutes = 60
        }
        val emulator = DanaRPumpEmulator(state)

        val response = emulator.processCommand(0x0205, ByteArray(0))

        assertThat(response[0].toInt() and 0xFF).isEqualTo(0x01) // running flag
        assertThat(response[1].toInt() and 0xFF).isEqualTo(120)  // percent
    }

    @Test
    fun statusReflectsDailyTotal() {
        val state = DanaRPumpState(DanaRVariant.DANA_R_V2).apply { dailyTotalUnits = 12.0 }
        val emulator = DanaRPumpEmulator(state)

        // 0x020B STATUS: bytes 0-2 are the daily total * 750, little-endian.
        val response = emulator.processCommand(0x020B, ByteArray(0))

        val daily = (response[0].toInt() and 0xFF) or (response[1].toInt() and 0xFF shl 8) or (response[2].toInt() and 0xFF shl 16)
        assertThat(daily).isEqualTo((12.0 * 750).toInt())
    }

    // --- APS history streaming (0xE003) ---

    @Test
    fun apsHistoryWithNoEventsReturnsDoneMarker() {
        // Empty history short-circuits: the done marker comes back inline, no streaming thread.
        val response = emulator().processCommand(0xE003, loadEverything())
        assertThat(response).isEqualTo(byteArrayOf(0xFF.toByte()))
    }

    @Test
    fun apsHistoryReturnsFirstEventInlineThenStreamsTheRestAndDoneMarker() {
        val state = DanaRPumpState(DanaRVariant.DANA_R_V2)
        state.historyStore.addEvent(DanaPump.HistoryEntry.BOLUS.value, TIMESTAMP, 150, 0)
        state.historyStore.addEvent(DanaPump.HistoryEntry.TEMP_START.value, TIMESTAMP + 1, 120, 60)
        val emulator = DanaRPumpEmulator(state)

        val streamed = Collections.synchronizedList(mutableListOf<ByteArray>())
        emulator.onAdditionalResponse = { _, data -> streamed += data }

        // First event inline: an 11-byte record, not the 1-byte done marker.
        val first = emulator.processCommand(0xE003, loadEverything())
        assertThat(first.size).isEqualTo(11)

        // The second event, then the done marker, arrive on the streaming thread.
        assertThat(await(2_000) { streamed.size == 2 }).isTrue()
        assertThat(streamed[0].size).isEqualTo(11)
        assertThat(streamed[1]).isEqualTo(byteArrayOf(0xFF.toByte()))
    }

    /** DanaR history "from" is all-zero to mean "everything"; MsgHistoryEventsV2 sends 6 zero bytes. */
    private fun loadEverything() = ByteArray(6)

    /** Polls [condition] until true or [timeoutMs] elapses — for the untracked streaming thread. */
    private fun await(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return false
    }

    @Test
    fun unknownCommandIsAcknowledged() {
        // The else branch: an unhandled opcode must not throw — it returns a benign ack.
        assertThat(emulator().processCommand(0x9999, ByteArray(0))).isEqualTo(byteArrayOf(0x00))
    }

    companion object {

        private const val TIMESTAMP = 1_700_000_000_000L
    }
}
