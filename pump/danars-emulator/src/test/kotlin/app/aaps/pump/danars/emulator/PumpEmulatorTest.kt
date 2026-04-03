package app.aaps.pump.danars.emulator

import app.aaps.pump.danars.encryption.BleEncryption
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PumpEmulatorTest {

    private lateinit var emulator: PumpEmulator
    private lateinit var state: PumpState

    @BeforeEach
    fun setup() {
        state = PumpState()
        emulator = PumpEmulator(state)
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
}
