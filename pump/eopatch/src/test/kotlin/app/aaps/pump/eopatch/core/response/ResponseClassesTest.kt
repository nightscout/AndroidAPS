package app.aaps.pump.eopatch.core.response

import app.aaps.pump.eopatch.core.code.PatchAeCode
import app.aaps.pump.eopatch.core.code.PatchBleResultCode
import app.aaps.pump.eopatch.core.scan.PatchSelfTestResult
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class ResponseClassesTest {

    // PatchBooleanResponse
    @Test
    fun `PatchBooleanResponse success should have SUCCESS code`() {
        val r = PatchBooleanResponse(true)
        assertThat(r.isSuccess).isTrue()
    }

    @Test
    fun `PatchBooleanResponse failure should have UNKNOWN_ERROR code`() {
        val r = PatchBooleanResponse(false)
        assertThat(r.isSuccess).isFalse()
        assertThat(r.resultCode).isEqualTo(PatchBleResultCode.UNKNOWN_ERROR)
    }

    // BolusResponse
    @Test
    fun `BolusResponse should store id`() {
        val r = BolusResponse(id = 42)
        assertThat(r.id).isEqualTo(42)
        assertThat(r.isSuccess).isTrue()
    }

    @Test
    fun `BolusResponse with error code should propagate`() {
        val r = BolusResponse(PatchBleResultCode.BOLUS_OVERRUN, 1)
        assertThat(r.resultCode).isEqualTo(PatchBleResultCode.BOLUS_OVERRUN)
    }

    // BolusStopResponse
    @Test
    fun `BolusStopResponse should calculate remain amount`() {
        val r = BolusStopResponse(1, 50, 10, 100)
        assertThat(r.injectedBolusAmount).isEqualTo(50)
        assertThat(r.injectingBolusAmount).isEqualTo(10)
        assertThat(r.targetBolusAmount).isEqualTo(100)
        assertThat(r.remainBolusAmount).isEqualTo(40) // 100 - 50 - 10
    }

    @Test
    fun `BolusStopResponse error constructor should zero amounts`() {
        val r = BolusStopResponse(1, PatchBleResultCode.BOLUS_UNKNOWN_ID)
        assertThat(r.injectedBolusAmount).isEqualTo(0)
        assertThat(r.remainBolusAmount).isEqualTo(0)
    }

    // BolusFinishTimeResponse
    @Test
    fun `BolusFinishTimeResponse should convert seconds to millis`() {
        val r = BolusFinishTimeResponse(60, 120)
        assertThat(r.nowBolusFinishTime).isEqualTo(60)
        assertThat(r.extBolusFinishTime).isEqualTo(120)
        assertThat(r.nowBolusFinishTimeInMillis).isEqualTo(60000)
        assertThat(r.extBolusFinishTimeInMillis).isEqualTo(120000)
    }

    // ComboBolusStartResponse
    @Test
    fun `ComboBolusStartResponse should store ids`() {
        val r = ComboBolusStartResponse(true, 1, 2)
        assertThat(r.id).isEqualTo(1)
    }

    // ComboBolusStopResponse
    @Test
    fun `ComboBolusStopResponse should store amounts`() {
        val r = ComboBolusStopResponse(1, 50, 10, 2, 30, 5)
        assertThat(r.injectedBolusAmount).isEqualTo(50)
        assertThat(r.injectedExBolusAmount).isEqualTo(30)
        assertThat(r.extId).isEqualTo(2)
    }

    @Test
    fun `ComboBolusStopResponse error constructor should zero amounts`() {
        val r = ComboBolusStopResponse(1, PatchBleResultCode.BOLUS_UNKNOWN_ID)
        assertThat(r.injectedBolusAmount).isEqualTo(0)
        assertThat(r.injectedExBolusAmount).isEqualTo(0)
    }

    // BasalScheduleSetResponse
    @Test
    fun `BasalScheduleSetResponse should store result code`() {
        val r = BasalScheduleSetResponse(PatchBleResultCode.SUCCESS)
        assertThat(r.isSuccess).isTrue()
    }

    // BasalStopResponse
    @Test
    fun `BasalStopResponse should store values`() {
        val r = BasalStopResponse(0xF000, 100, 1, PatchBleResultCode.SUCCESS)
        assertThat(r.id).isEqualTo(0xF000)
        assertThat(r.unit_c).isEqualTo(100)
        assertThat(r.unit_w).isEqualTo(1)
    }

    // BasalHistoryResponse
    @Test
    fun `BasalHistoryResponse should store seq and dose values`() {
        val doses = floatArrayOf(0.05f, 0.10f, 0.15f)
        val r = BasalHistoryResponse(42, doses, false)
        assertThat(r.seq).isEqualTo(42)
        assertThat(r.injectedDoseValues).isEqualTo(doses)
    }

    // BasalHistoryIndexResponse
    @Test
    fun `BasalHistoryIndexResponse should calculate lastFinishedIndex`() {
        val r = BasalHistoryIndexResponse(0, 5)
        assertThat(r.lastIndex).isEqualTo(0)
        assertThat(r.curIndex).isEqualTo(5)
        assertThat(r.lastFinishedIndex).isEqualTo(4)
    }

    @Test
    fun `BasalHistoryIndexResponse with curIndex 0 should give -1 lastFinished`() {
        val r = BasalHistoryIndexResponse(0, 0)
        assertThat(r.lastFinishedIndex).isEqualTo(-1)
    }

    @Test
    fun `BasalHistoryIndexResponse with curIndex 0xFFFF should give -1 lastFinished`() {
        val r = BasalHistoryIndexResponse(0, 0xFFFF)
        assertThat(r.lastFinishedIndex).isEqualTo(-1)
    }

    // TempBasalScheduleSetResponse
    @Test
    fun `TempBasalScheduleSetResponse should store result code`() {
        val r = TempBasalScheduleSetResponse(PatchBleResultCode.SUCCESS)
        assertThat(r.isSuccess).isTrue()
    }

    // TempBasalFinishTimeResponse
    @Test
    fun `TempBasalFinishTimeResponse should store finish time`() {
        val r = TempBasalFinishTimeResponse(3600, PatchBleResultCode.SUCCESS)
        assertThat(r.tempBasalFinishTime).isEqualTo(3600)
    }

    // BondingResponse
    @Test
    fun `BondingResponse should store state`() {
        val r = BondingResponse(PatchBleResultCode.SUCCESS, 0)
        assertThat(r.isSuccess).isTrue()
    }

    // FirmwareVersionResponse
    @Test
    fun `FirmwareVersionResponse should format version string`() {
        val r = FirmwareVersionResponse(true, 1, 2, 3, 4)
        assertThat(r.firmwareVersionString).isEqualTo("1.2.3.4")
    }

    @Test
    fun `FirmwareVersionResponse failure should return null version`() {
        val r = FirmwareVersionResponse(false, 0, 0, 0, 0)
        assertThat(r.firmwareVersionString).isNull()
    }

    // GlobalTimeResponse
    @Test
    fun `GlobalTimeResponse should convert seconds to millis`() {
        val r = GlobalTimeResponse(1000, 36)
        assertThat(r.globalTime).isEqualTo(1000)
        assertThat(r.timeZoneOffset).isEqualTo(36)
        assertThat(r.globalTimeInMilli).isEqualTo(1000000)
    }

    // KeyResponse
    @Test
    fun `KeyResponse should store key and code`() {
        val key = byteArrayOf(0x01, 0x02)
        val r = KeyResponse.create(key, 42)
        assertThat(r.publicKey).isEqualTo(key)
        assertThat(r.code).isEqualTo(42)
        assertThat(r.sequence).isEqualTo(42)
    }

    @Test
    fun `KeyResponse create from bytes should extract 15-bit sequence`() {
        val r = KeyResponse.create(0x01.toByte(), 0x00.toByte())
        assertThat(r.sequence).isEqualTo(256) // (0x01 & 0x7F) << 8 | 0x00
    }

    // LotNumberResponse
    @Test
    fun `LotNumberResponse should store lot number`() {
        val r = LotNumberResponse(true, "LOT123")
        assertThat(r.lotNumber).isEqualTo("LOT123")
        assertThat(r.isSuccess).isTrue()
    }

    // ModelNameResponse
    @Test
    fun `ModelNameResponse should parse null-terminated bytes`() {
        val bytes = byteArrayOf(0x41, 0x42, 0x43, 0x00, 0x00) // "ABC\0\0"
        val r = ModelNameResponse(true, bytes)
        assertThat(r.modelName).isEqualTo("ABC")
    }

    @Test
    fun `ModelNameResponse failure should give empty string`() {
        val r = ModelNameResponse(false, byteArrayOf())
        assertThat(r.modelName).isEmpty()
    }

    // SerialNumberResponse
    @Test
    fun `SerialNumberResponse should store serial`() {
        val r = SerialNumberResponse(true, "SN001")
        assertThat(r.serialNumber).isEqualTo("SN001")
        assertThat(r.isSuccess).isTrue()
    }

    // TemperatureResponse
    @Test
    fun `TemperatureResponse should always return TEST_SUCCESS`() {
        val r = TemperatureResponse(25)
        assertThat(r.temperature).isEqualTo(25)
        assertThat(r.result).isEqualTo(PatchSelfTestResult.TEST_SUCCESS)
    }

    // BatteryVoltageLevelPairingResponse
    @Test
    fun `BatteryVoltageLevelPairingResponse should return VOLTAGE_MIN on error`() {
        val r = BatteryVoltageLevelPairingResponse(3300, 50, 1)
        assertThat(r.result).isEqualTo(PatchSelfTestResult.VOLTAGE_MIN)
    }

    @Test
    fun `BatteryVoltageLevelPairingResponse should return TEST_SUCCESS when no error`() {
        val r = BatteryVoltageLevelPairingResponse(3700, 80, 0)
        assertThat(r.result).isEqualTo(PatchSelfTestResult.TEST_SUCCESS)
    }

    // PumpDurationResponse
    @Test
    fun `PumpDurationResponse should store durations`() {
        val r = PumpDurationResponse(100, 200, 150)
        assertThat(r.durationS).isEqualTo(100)
        assertThat(r.durationL).isEqualTo(200)
        assertThat(r.durationM).isEqualTo(150)
    }

    // WakeUpTimeResponse
    @Test
    fun `WakeUpTimeResponse should convert to millis`() {
        val r = WakeUpTimeResponse(true, 3600)
        assertThat(r.timeInMillis).isEqualTo(TimeUnit.SECONDS.toMillis(3600))
    }

    // PatchInternalSuspendTimeResponse
    @Test
    fun `PatchInternalSuspendTimeResponse should store total seconds`() {
        val r = PatchInternalSuspendTimeResponse(true, 7200)
        assertThat(r.totalSeconds).isEqualTo(7200)
        assertThat(r.isSuccess).isTrue()
    }

    // UpdateConnectionResponse
    @Test
    fun `UpdateConnectionResponse should copy state bytes`() {
        val bytes = ByteArray(20) { it.toByte() }
        val r = UpdateConnectionResponse(bytes)
        assertThat(r.patchState).isEqualTo(bytes)
    }

    // AeCodeResponse
    @Test
    fun `AeCodeResponse should return alarm codes`() {
        val codes = setOf(PatchAeCode.create(107, 0), PatchAeCode.create(208, 0))
        val r = AeCodeResponse(codes, 2)
        assertThat(r.alarmCodes).hasSize(2)
        assertThat(r.alarmCount).isEqualTo(2)
    }
}
