package app.aaps.pump.danars.emulator

import app.aaps.pump.dana.emulator.ReviewRecordCodes
import app.aaps.pump.danars.encryption.BleEncryption
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Collections
import kotlin.time.Instant

class PumpEmulatorTest {

    private lateinit var emulator: PumpEmulator
    private lateinit var state: PumpState

    /** Pump-to-app messages the emulator sends on its own, in order. See the review-history tests. */
    private val spontaneous = Collections.synchronizedList(mutableListOf<SpontaneousMessage>())

    @BeforeEach
    fun setup() {
        state = PumpState()
        emulator = PumpEmulator(state)
        // Recorded rather than asserted on directly, because they arrive on the emulator's own
        // threads — every test that reads them awaits those first.
        emulator.onSpontaneousMessage = { type, opCode, data -> spontaneous += SpontaneousMessage(type, opCode, data) }
        emulator.historyEventDelayMs = 0
    }

    @AfterEach
    fun tearDown() {
        emulator.cancelPendingWork()
    }

    @Test
    fun keepConnectionReturnsOk() {
        val response = emulator.processCommand(BleEncryption.DANAR_PACKET__OPCODE_ETC__KEEP_CONNECTION, ByteArray(0))
        assertThat(response).isEqualTo(byteArrayOf(0x00))
    }

    @Test
    fun getShippingInformationReturnsSerialNumber() {
        state.serialNumber = "AAA12345BB"
        val response = emulator.processCommand(BleEncryption.DANAR_PACKET__OPCODE_REVIEW__GET_SHIPPING_INFORMATION, ByteArray(0))
        assertThat(response.size).isEqualTo(18)
        val serial = String(response, 0, 10, Charsets.UTF_8)
        assertThat(serial).isEqualTo("AAA12345BB")
    }

    @Test
    fun getPumpCheckReturnsHwModel() {
        state.hwModel = 0x05
        state.protocol = 5
        state.productCode = 0
        val response = emulator.processCommand(BleEncryption.DANAR_PACKET__OPCODE_REVIEW__GET_PUMP_CHECK, ByteArray(0))
        assertThat(response[0]).isEqualTo(0x05.toByte())
        assertThat(response[1]).isEqualTo(5.toByte())
    }

    @Test
    fun getProfileNumberReturnsActiveProfile() {
        state.activeProfileNumber = 2
        val response = emulator.processCommand(BleEncryption.DANAR_PACKET__OPCODE_BASAL__GET_PROFILE_NUMBER, ByteArray(0))
        assertThat(response[0]).isEqualTo(2.toByte())
    }

    @Test
    fun getBasalRateReturnsProfileData() {
        state.maxBasal = 3.0
        state.basalStep = 0.01
        state.basalProfiles[0][0] = 1.5
        state.basalProfiles[0][12] = 2.0
        val response = emulator.processCommand(BleEncryption.DANAR_PACKET__OPCODE_BASAL__GET_BASAL_RATE, ByteArray(0))
        assertThat(response.size).isEqualTo(51)
        // max basal = 300 (3.0 * 100) = 0x2C, 0x01
        assertThat(response[0].toInt() and 0xFF).isEqualTo(0x2C)
        assertThat(response[1].toInt() and 0xFF).isEqualTo(0x01)
        // basal step = 1 (0.01 * 100)
        assertThat(response[2].toInt() and 0xFF).isEqualTo(1)
        // first hour = 150 (1.5 * 100) = 0x96, 0x00
        assertThat(response[3].toInt() and 0xFF).isEqualTo(0x96)
        assertThat(response[4].toInt() and 0xFF).isEqualTo(0x00)
        // hour 12 = 200 (2.0 * 100) = 0xC8, 0x00
        assertThat(response[3 + 24].toInt() and 0xFF).isEqualTo(0xC8)
    }

    @Test
    fun setTemporaryBasalUpdatesState() {
        val params = byteArrayOf(150.toByte(), 2) // 150%, 2 hours
        emulator.processCommand(BleEncryption.DANAR_PACKET__OPCODE_BASAL__SET_TEMPORARY_BASAL, params)
        assertThat(state.isTempBasalRunning).isTrue()
        assertThat(state.tempBasalPercent).isEqualTo(150)
    }

    @Test
    fun cancelTemporaryBasalClearsState() {
        state.isTempBasalRunning = true
        state.tempBasalPercent = 150
        emulator.processCommand(BleEncryption.DANAR_PACKET__OPCODE_BASAL__CANCEL_TEMPORARY_BASAL, ByteArray(0))
        assertThat(state.isTempBasalRunning).isFalse()
        assertThat(state.tempBasalPercent).isEqualTo(0)
    }

    @Test
    fun apsSetTemporaryBasalUpdatesState() {
        // 200% = 0xC8, 0x00; duration param 150 = 15min
        val params = byteArrayOf(0xC8.toByte(), 0x00, 150.toByte())
        emulator.processCommand(BleEncryption.DANAR_PACKET__OPCODE_BASAL__APS_SET_TEMPORARY_BASAL, params)
        assertThat(state.isTempBasalRunning).isTrue()
        assertThat(state.tempBasalPercent).isEqualTo(200)
        assertThat(state.tempBasalDurationMinutes).isEqualTo(15)
    }

    @Test
    fun setStepBolusStartUpdatesDailyTotals() {
        state.dailyTotalUnits = 5.0
        state.reservoirRemainingUnits = 150.0
        // 2.5U = 250 = 0xFA, 0x00; speed = 0
        val params = byteArrayOf(0xFA.toByte(), 0x00, 0x00)
        emulator.processCommand(BleEncryption.DANAR_PACKET__OPCODE_BOLUS__SET_STEP_BOLUS_START, params)
        assertThat(state.lastBolusAmount).isEqualTo(2.5)
        assertThat(state.dailyTotalUnits).isEqualTo(7.5)
        assertThat(state.reservoirRemainingUnits).isEqualTo(147.5)
    }

    @Test
    fun setExtendedBolusUpdatesState() {
        // 1.0U = 100 = 0x64, 0x00; duration = 4 half-hours = 2h
        val params = byteArrayOf(0x64, 0x00, 0x04)
        emulator.processCommand(BleEncryption.DANAR_PACKET__OPCODE_BOLUS__SET_EXTENDED_BOLUS, params)
        assertThat(state.isExtendedBolusRunning).isTrue()
        assertThat(state.extendedBolusAmount).isEqualTo(1.0)
        assertThat(state.extendedBolusDurationHalfHours).isEqualTo(4)
    }

    @Test
    fun cancelExtendedBolusClearsState() {
        state.isExtendedBolusRunning = true
        state.extendedBolusAmount = 1.0
        emulator.processCommand(BleEncryption.DANAR_PACKET__OPCODE_BOLUS__SET_EXTENDED_BOLUS_CANCEL, ByteArray(0))
        assertThat(state.isExtendedBolusRunning).isFalse()
        assertThat(state.extendedBolusAmount).isEqualTo(0.0)
    }

    @Test
    fun initialScreenInformationReflectsState() {
        state.isTempBasalRunning = true
        state.tempBasalPercent = 130
        state.batteryRemaining = 75
        state.reservoirRemainingUnits = 120.0
        state.dailyTotalUnits = 10.0
        state.maxDailyTotalUnits = 25.0
        state.currentBasal = 1.2

        val response = emulator.processCommand(BleEncryption.DANAR_PACKET__OPCODE_REVIEW__INITIAL_SCREEN_INFORMATION, ByteArray(0))

        // Status byte: temp basal running = 0x10
        assertThat(response[0].toInt() and 0xFF).isEqualTo(0x10)
        // Battery
        assertThat(response[10].toInt() and 0xFF).isEqualTo(75)
        // Temp basal percent
        assertThat(response[9].toInt() and 0xFF).isEqualTo(130)
    }

    @Test
    fun setProfileBasalRateUpdatesProfile() {
        val params = ByteArray(49)
        params[0] = 1 // profile number 1
        // Set hour 0 to 1.5U/h = 150 = 0x96, 0x00
        params[1] = 0x96.toByte()
        params[2] = 0x00
        emulator.processCommand(BleEncryption.DANAR_PACKET__OPCODE_BASAL__SET_PROFILE_BASAL_RATE, params)
        assertThat(state.basalProfiles[1][0]).isEqualTo(1.5)
    }

    @Test
    fun setProfileNumberUpdatesActiveProfile() {
        emulator.processCommand(BleEncryption.DANAR_PACKET__OPCODE_BASAL__SET_PROFILE_NUMBER, byteArrayOf(2))
        assertThat(state.activeProfileNumber).isEqualTo(2)
    }

    @Test
    fun getUserOptionReturnsSettings() {
        state.units = 1 // mmol/L
        state.lowReservoirRate = 20
        state.batteryRemaining = 80
        val response = emulator.processCommand(BleEncryption.DANAR_PACKET__OPCODE_OPTION__GET_USER_OPTION, ByteArray(0))
        assertThat(response[6].toInt()).isEqualTo(1) // units = mmol/L
        assertThat(response[8].toInt()).isEqualTo(20) // low reservoir rate
    }

    @Test
    fun setUserOptionUpdatesState() {
        val params = ByteArray(15)
        params[6] = 1 // units = mmol/L
        params[8] = 15 // low reservoir rate
        emulator.processCommand(BleEncryption.DANAR_PACKET__OPCODE_OPTION__SET_USER_OPTION, params)
        assertThat(state.units).isEqualTo(1)
        assertThat(state.lowReservoirRate).isEqualTo(15)
    }

    @Test
    fun apsHistoryEventsReturnsDone() {
        val response = emulator.processCommand(BleEncryption.DANAR_PACKET__OPCODE__APS_HISTORY_EVENTS, ByteArray(0))
        assertThat(response[0]).isEqualTo(0xFF.toByte())
    }

    @Test
    fun unknownCommandReturnsOk() {
        val response = emulator.processCommand(0x99, ByteArray(0))
        assertThat(response).isEqualTo(byteArrayOf(0x00))
    }

    // --- Review history (the per-type pump history screen) ---
    //
    // The reply is a stream: the first record comes back from processCommand, any further ones plus
    // the end marker arrive as spontaneous messages. DanaRSService.loadHistory blocks until it sees
    // the marker, so "the marker is always sent" is the load-bearing property here, empty or not.

    @Test
    fun reviewHistoryWithNoRecordsReturnsEndMarkerOnly() {
        val response = emulator.processCommand(BleEncryption.DANAR_PACKET__OPCODE_REVIEW__ALARM, loadEverything())

        assertThat(response).isEqualTo(byteArrayOf(0x00))
        emulator.awaitPendingCallbacks()
        assertThat(spontaneous).isEmpty()
    }

    @Test
    fun reviewHistoryReturnsSeededRecordInline() {
        state.reviewHistoryStore.addEvent(
            code = ReviewRecordCodes.ALARM,
            timestamp = localTimestamp(2026, 7, 16, 21, 11, 28),
            param1 = 250,
            param2 = ReviewRecordCodes.Alarm.OCCLUSION
        )

        val response = emulator.processCommand(BleEncryption.DANAR_PACKET__OPCODE_REVIEW__ALARM, loadEverything())

        // The layout DanaRSPacketHistory.handleMessage reads: code, then the local date/time it
        // rebuilds with DateTime(2000 + year, ...), then the sub-code and a big-endian value.
        assertThat(response.size).isEqualTo(10)
        assertThat(response[0].toInt() and 0xFF).isEqualTo(ReviewRecordCodes.ALARM)
        assertThat(response[1].toInt() and 0xFF).isEqualTo(26)
        assertThat(response[2].toInt() and 0xFF).isEqualTo(7)
        assertThat(response[3].toInt() and 0xFF).isEqualTo(16)
        assertThat(response[4].toInt() and 0xFF).isEqualTo(21)
        assertThat(response[5].toInt() and 0xFF).isEqualTo(11)
        assertThat(response[6].toInt() and 0xFF).isEqualTo(28)
        assertThat(response[7].toInt() and 0xFF).isEqualTo(ReviewRecordCodes.Alarm.OCCLUSION)
        assertThat(response[8].toInt() and 0xFF).isEqualTo(0)
        assertThat(response[9].toInt() and 0xFF).isEqualTo(250)
    }

    @Test
    fun reviewHistoryReturnsOnlyTheRequestedType() {
        state.reviewHistoryStore.addEvent(ReviewRecordCodes.ALARM, NOW, 0, ReviewRecordCodes.Alarm.OCCLUSION)
        state.reviewHistoryStore.addEvent(ReviewRecordCodes.BOLUS, NOW, 150, ReviewRecordCodes.BolusType.STANDARD)

        val bolus = emulator.processCommand(BleEncryption.DANAR_PACKET__OPCODE_REVIEW__BOLUS, loadEverything())
        emulator.awaitPendingCallbacks()

        // Each REVIEW__* command asks for one type and a real pump answers with that type only. The
        // driver would not notice a mix — it classifies on the record's own code, not the opcode it
        // asked under — so nothing downstream would catch this regressing.
        assertThat(bolus[0].toInt() and 0xFF).isEqualTo(ReviewRecordCodes.BOLUS)
        assertThat(recordCodesOf(spontaneous)).doesNotContain(ReviewRecordCodes.ALARM)
    }

    @Test
    fun reviewHistoryStreamsRemainingRecordsThenEndMarker() {
        repeat(3) { i ->
            state.reviewHistoryStore.addEvent(
                code = ReviewRecordCodes.BOLUS,
                timestamp = NOW + i,
                param1 = 100 + i,
                param2 = ReviewRecordCodes.BolusType.STANDARD
            )
        }

        val first = emulator.processCommand(BleEncryption.DANAR_PACKET__OPCODE_REVIEW__BOLUS, loadEverything())
        emulator.awaitPendingCallbacks()

        assertThat(first[9].toInt() and 0xFF).isEqualTo(100)
        // The two it did not return inline, then the marker.
        assertThat(spontaneous).hasSize(3)
        assertThat(spontaneous[0].data[9].toInt() and 0xFF).isEqualTo(101)
        assertThat(spontaneous[1].data[9].toInt() and 0xFF).isEqualTo(102)
        assertThat(spontaneous[2].data).isEqualTo(byteArrayOf(0x00))
        // All under the opcode that was asked for, or BLEComm routes them to the wrong packet.
        assertThat(spontaneous.map { it.opCode }.distinct())
            .containsExactly(BleEncryption.DANAR_PACKET__OPCODE_REVIEW__BOLUS)
        assertThat(spontaneous.map { it.type }.distinct())
            .containsExactly(BleEncryption.DANAR_PACKET__TYPE_RESPONSE)
    }

    @Test
    fun reviewHistoryExcludesRecordsOlderThanRequested() {
        val cutoff = localTimestamp(2026, 7, 16, 12, 0, 0)
        state.reviewHistoryStore.addEvent(ReviewRecordCodes.BOLUS, cutoff - 60_000, 100, ReviewRecordCodes.BolusType.STANDARD)
        state.reviewHistoryStore.addEvent(ReviewRecordCodes.BOLUS, cutoff + 60_000, 200, ReviewRecordCodes.BolusType.STANDARD)

        val response = emulator.processCommand(BleEncryption.DANAR_PACKET__OPCODE_REVIEW__BOLUS, fromParams(cutoff))
        emulator.awaitPendingCallbacks()

        // Only the newer one, inline, then the marker — nothing else.
        assertThat(response[9].toInt() and 0xFF).isEqualTo(200)
        assertThat(spontaneous).hasSize(1)
        assertThat(spontaneous[0].data).isEqualTo(byteArrayOf(0x00))
    }

    @Test
    fun reviewHistoryAndApsEventHistoryAreSeparateStores() {
        state.reviewHistoryStore.addEvent(ReviewRecordCodes.BOLUS, NOW, 150, ReviewRecordCodes.BolusType.STANDARD)

        // Same class backs both, so nothing but the separate instances stops a record seeded for one
        // screen from turning up in the other — where it would be read in a different wire format.
        val apsHistory = emulator.processCommand(BleEncryption.DANAR_PACKET__OPCODE__APS_HISTORY_EVENTS, loadEverything())

        assertThat(apsHistory).isEqualTo(byteArrayOf(0xFF.toByte()))
    }

    /**
     * The request every history load actually sends: `DanaRSService.loadHistory` never calls
     * `with(from)`, so the driver asks for all six bytes zeroed and
     * `HistoryEventStore.parseFromTimestamp` reads that as "from the beginning".
     */
    private fun loadEverything() = ByteArray(6)

    /**
     * A "from" request for [millis], encoded the way [HistoryEventStore.parseFromTimestamp] reads
     * it — as **UTC**. Note the driver only sends UTC fields when the pump is `usingUTC` (Dana-i);
     * for the others it sends local time, which the store would then misread by the zone offset.
     * That mismatch is dormant today (no review-history load sends a non-zero "from") and is not
     * what this test is about, so this stays on the store's own terms.
     */
    private fun fromParams(millis: Long): ByteArray {
        val d = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC)
        return byteArrayOf(
            (d.year - 2000).toByte(), d.month.number.toByte(), d.day.toByte(),
            d.hour.toByte(), d.minute.toByte(), d.second.toByte()
        )
    }

    /** Records are written in local time, so build the instants in the same zone to stay TZ-agnostic. */
    private fun localTimestamp(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long =
        LocalDateTime(year, month, day, hour, minute, second)
            .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    private fun recordCodesOf(messages: List<SpontaneousMessage>) =
        messages.filter { it.data.size == 10 }.map { it.data[0].toInt() and 0xFF }

    private data class SpontaneousMessage(val type: Int, val opCode: Int, val data: ByteArray)

    companion object {

        /** Any fixed instant: tests that do not assert on the date only need records to sort. */
        private val NOW = LocalDateTime(2026, 7, 16, 12, 0, 0)
            .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    }
}
