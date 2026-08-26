package app.aaps.pump.carelevo.domain.model.bt

import app.aaps.pump.carelevo.domain.model.basal.CarelevoBasalSegment
import app.aaps.pump.carelevo.domain.model.bt.AlertMessageResult.Companion.codeToAlertMessageCommand
import app.aaps.pump.carelevo.domain.model.bt.AlertMessageResult.Companion.commandToCode as alertToCode
import app.aaps.pump.carelevo.domain.model.bt.InfusionInfoResult.Companion.codeToInfusionInfoCommand
import app.aaps.pump.carelevo.domain.model.bt.InfusionInfoResult.Companion.commandToCode as infusionInfoToCode
import app.aaps.pump.carelevo.domain.model.bt.InfusionModeResult.Companion.codeToInfusionModeCommand
import app.aaps.pump.carelevo.domain.model.bt.InfusionModeResult.Companion.commandToCode as infusionModeToCode
import app.aaps.pump.carelevo.domain.model.bt.NoticeMessageResult.Companion.codeToNoticeMessageCommand
import app.aaps.pump.carelevo.domain.model.bt.NoticeMessageResult.Companion.commandToCode as noticeToCode
import app.aaps.pump.carelevo.domain.model.bt.PumpStateResult.Companion.codeToPumpStateCommand
import app.aaps.pump.carelevo.domain.model.bt.PumpStateResult.Companion.commandToCode as pumpStateToCode
import app.aaps.pump.carelevo.domain.model.bt.Result.Companion.codeToResultCommand
import app.aaps.pump.carelevo.domain.model.bt.Result.Companion.commandToCode as resultToCode
import app.aaps.pump.carelevo.domain.model.bt.SafetyCheckResult.Companion.codeToSafetyCheckCommand
import app.aaps.pump.carelevo.domain.model.bt.SafetyCheckResult.Companion.commandToCode as safetyToCode
import app.aaps.pump.carelevo.domain.model.bt.SetBasalProgramResult.Companion.codeToSetBasalProgramCommand
import app.aaps.pump.carelevo.domain.model.bt.SetBasalProgramResult.Companion.commandToCode as setBasalToCode
import app.aaps.pump.carelevo.domain.model.bt.SetBolusProgramResult.Companion.codeToSetBolusProgramCommand
import app.aaps.pump.carelevo.domain.model.bt.SetBolusProgramResult.Companion.commandToCode as setBolusToCode
import app.aaps.pump.carelevo.domain.model.bt.StopPumpResult.Companion.codeToStopPumpCommand
import app.aaps.pump.carelevo.domain.model.bt.StopPumpResult.Companion.commandToCode as stopPumpToCode
import app.aaps.pump.carelevo.domain.model.bt.WarningMessageResult.Companion.codeToWarningMessageCommand
import app.aaps.pump.carelevo.domain.model.bt.WarningMessageResult.Companion.commandToCode as warningToCode
import app.aaps.pump.carelevo.domain.type.AlarmCause
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure-logic unit tests for the Carelevo BT domain layer:
 *  - every enum <-> wire-code mapping in [CarelevoBtEnums] (both directions incl. the `else` default),
 *  - protocol classification in [isPatchProtocol] / [isBasalProtocol] / [isBolusProtocol],
 *  - the response -> result-model dispatch in [createPatchResultModel] / [createBasalResultModel] /
 *    [createBolusResultModel] (every branch + the null fall-through),
 *  - data-class construction for the request DTOs.
 */
internal class CarelevoBtModelTest {

    private val ts = 1_000L
    private val patchCmd = 0x11   // isPatchProtocol == true
    private val basalCmd = 0x13   // isBasalProtocol == true
    private val bolusCmd = 0x24   // isBolusProtocol == true

    // ---------------------------------------------------------------------------------------------
    // Result
    // ---------------------------------------------------------------------------------------------

    @Test fun `Result commandToCode maps every value`() {
        assertThat(Result.SUCCESS.resultToCode()).isEqualTo(0)
        assertThat(Result.FAILED.resultToCode()).isEqualTo(1)
    }

    @Test fun `Result codeToResultCommand maps codes and default`() {
        assertThat(0.codeToResultCommand()).isEqualTo(Result.SUCCESS)
        assertThat(1.codeToResultCommand()).isEqualTo(Result.FAILED)
        assertThat(99.codeToResultCommand()).isEqualTo(Result.FAILED)
        assertThat((-5).codeToResultCommand()).isEqualTo(Result.FAILED)
    }

    // ---------------------------------------------------------------------------------------------
    // SafetyCheckResult
    // ---------------------------------------------------------------------------------------------

    @Test fun `SafetyCheckResult commandToCode maps every value`() {
        assertThat(SafetyCheckResult.SUCCESS.safetyToCode()).isEqualTo(0)
        assertThat(SafetyCheckResult.INSULIN_DEFICIENCY.safetyToCode()).isEqualTo(1)
        assertThat(SafetyCheckResult.EXPIRED.safetyToCode()).isEqualTo(2)
        assertThat(SafetyCheckResult.LOW_VOLTAGE.safetyToCode()).isEqualTo(3)
        assertThat(SafetyCheckResult.PATCH_ERROR.safetyToCode()).isEqualTo(11)
        assertThat(SafetyCheckResult.PUMP_ERROR.safetyToCode()).isEqualTo(12)
        assertThat(SafetyCheckResult.REP_REQUEST.safetyToCode()).isEqualTo(4)
        assertThat(SafetyCheckResult.REP_REQUEST1.safetyToCode()).isEqualTo(18)
        assertThat(SafetyCheckResult.FAILED.safetyToCode()).isEqualTo(-1)
    }

    @Test fun `SafetyCheckResult codeToSafetyCheckCommand maps codes and default`() {
        assertThat(0.codeToSafetyCheckCommand()).isEqualTo(SafetyCheckResult.SUCCESS)
        assertThat(1.codeToSafetyCheckCommand()).isEqualTo(SafetyCheckResult.INSULIN_DEFICIENCY)
        assertThat(2.codeToSafetyCheckCommand()).isEqualTo(SafetyCheckResult.EXPIRED)
        assertThat(3.codeToSafetyCheckCommand()).isEqualTo(SafetyCheckResult.LOW_VOLTAGE)
        assertThat(11.codeToSafetyCheckCommand()).isEqualTo(SafetyCheckResult.PATCH_ERROR)
        assertThat(12.codeToSafetyCheckCommand()).isEqualTo(SafetyCheckResult.PUMP_ERROR)
        assertThat(4.codeToSafetyCheckCommand()).isEqualTo(SafetyCheckResult.REP_REQUEST)
        assertThat(18.codeToSafetyCheckCommand()).isEqualTo(SafetyCheckResult.REP_REQUEST1)
        assertThat(99.codeToSafetyCheckCommand()).isEqualTo(SafetyCheckResult.FAILED)
    }

    // ---------------------------------------------------------------------------------------------
    // SetBasalProgramResult
    // ---------------------------------------------------------------------------------------------

    @Test fun `SetBasalProgramResult commandToCode maps every value`() {
        assertThat(SetBasalProgramResult.SUCCESS.setBasalToCode()).isEqualTo(0)
        assertThat(SetBasalProgramResult.INSULIN_DEFICIENCY.setBasalToCode()).isEqualTo(1)
        assertThat(SetBasalProgramResult.EXPIRED.setBasalToCode()).isEqualTo(2)
        assertThat(SetBasalProgramResult.LOW_VOLTAGE.setBasalToCode()).isEqualTo(3)
        assertThat(SetBasalProgramResult.ABNORMAL_TEMP.setBasalToCode()).isEqualTo(4)
        assertThat(SetBasalProgramResult.PUMP_ERROR.setBasalToCode()).isEqualTo(12)
        assertThat(SetBasalProgramResult.ABNORMAL_PROGRAM.setBasalToCode()).isEqualTo(19)
        assertThat(SetBasalProgramResult.EXCEED_LIMIT.setBasalToCode()).isEqualTo(20)
        assertThat(SetBasalProgramResult.FAILED.setBasalToCode()).isEqualTo(-1)
    }

    @Test fun `SetBasalProgramResult codeToSetBasalProgramCommand maps codes and default`() {
        assertThat(0.codeToSetBasalProgramCommand()).isEqualTo(SetBasalProgramResult.SUCCESS)
        assertThat(1.codeToSetBasalProgramCommand()).isEqualTo(SetBasalProgramResult.INSULIN_DEFICIENCY)
        assertThat(2.codeToSetBasalProgramCommand()).isEqualTo(SetBasalProgramResult.EXPIRED)
        assertThat(3.codeToSetBasalProgramCommand()).isEqualTo(SetBasalProgramResult.LOW_VOLTAGE)
        assertThat(4.codeToSetBasalProgramCommand()).isEqualTo(SetBasalProgramResult.ABNORMAL_TEMP)
        assertThat(12.codeToSetBasalProgramCommand()).isEqualTo(SetBasalProgramResult.PUMP_ERROR)
        assertThat(19.codeToSetBasalProgramCommand()).isEqualTo(SetBasalProgramResult.ABNORMAL_PROGRAM)
        assertThat(20.codeToSetBasalProgramCommand()).isEqualTo(SetBasalProgramResult.EXCEED_LIMIT)
        assertThat(77.codeToSetBasalProgramCommand()).isEqualTo(SetBasalProgramResult.FAILED)
    }

    // ---------------------------------------------------------------------------------------------
    // SetBolusProgramResult
    // ---------------------------------------------------------------------------------------------

    @Test fun `SetBolusProgramResult commandToCode maps every value`() {
        assertThat(SetBolusProgramResult.SUCCESS.setBolusToCode()).isEqualTo(0)
        assertThat(SetBolusProgramResult.INSULIN_DEFICIENCY.setBolusToCode()).isEqualTo(1)
        assertThat(SetBolusProgramResult.EXPIRED.setBolusToCode()).isEqualTo(2)
        assertThat(SetBolusProgramResult.LOW_VOLTAGE.setBolusToCode()).isEqualTo(3)
        assertThat(SetBolusProgramResult.ABNORMAL_TEMP.setBolusToCode()).isEqualTo(4)
        assertThat(SetBolusProgramResult.PUMP_ERROR.setBolusToCode()).isEqualTo(12)
        assertThat(SetBolusProgramResult.EXCEED_LIMIT.setBolusToCode()).isEqualTo(20)
        assertThat(SetBolusProgramResult.FAILED.setBolusToCode()).isEqualTo(-1)
    }

    @Test fun `SetBolusProgramResult codeToSetBolusProgramCommand maps codes and default`() {
        assertThat(0.codeToSetBolusProgramCommand()).isEqualTo(SetBolusProgramResult.SUCCESS)
        assertThat(1.codeToSetBolusProgramCommand()).isEqualTo(SetBolusProgramResult.INSULIN_DEFICIENCY)
        assertThat(2.codeToSetBolusProgramCommand()).isEqualTo(SetBolusProgramResult.EXPIRED)
        assertThat(3.codeToSetBolusProgramCommand()).isEqualTo(SetBolusProgramResult.LOW_VOLTAGE)
        assertThat(4.codeToSetBolusProgramCommand()).isEqualTo(SetBolusProgramResult.ABNORMAL_TEMP)
        assertThat(12.codeToSetBolusProgramCommand()).isEqualTo(SetBolusProgramResult.PUMP_ERROR)
        assertThat(20.codeToSetBolusProgramCommand()).isEqualTo(SetBolusProgramResult.EXCEED_LIMIT)
        assertThat(50.codeToSetBolusProgramCommand()).isEqualTo(SetBolusProgramResult.FAILED)
    }

    // ---------------------------------------------------------------------------------------------
    // StopPumpResult
    // ---------------------------------------------------------------------------------------------

    @Test fun `StopPumpResult commandToCode maps every value`() {
        assertThat(StopPumpResult.BY_REQ.stopPumpToCode()).isEqualTo(0)
        assertThat(StopPumpResult.INSULIN_DEFICIENCY.stopPumpToCode()).isEqualTo(1)
        assertThat(StopPumpResult.ABNORMAL_PUMP.stopPumpToCode()).isEqualTo(2)
        assertThat(StopPumpResult.LOW_VOLTAGE.stopPumpToCode()).isEqualTo(3)
        assertThat(StopPumpResult.ABNORMAL_TEMP.stopPumpToCode()).isEqualTo(4)
        assertThat(StopPumpResult.NOT_USED.stopPumpToCode()).isEqualTo(5)
        assertThat(StopPumpResult.PUMP_ERROR.stopPumpToCode()).isEqualTo(12)
        assertThat(StopPumpResult.BY_LGS.stopPumpToCode()).isEqualTo(29)
        assertThat(StopPumpResult.ERROR.stopPumpToCode()).isEqualTo(-1)
    }

    @Test fun `StopPumpResult codeToStopPumpCommand maps codes and default`() {
        assertThat(0.codeToStopPumpCommand()).isEqualTo(StopPumpResult.BY_REQ)
        assertThat(1.codeToStopPumpCommand()).isEqualTo(StopPumpResult.INSULIN_DEFICIENCY)
        assertThat(2.codeToStopPumpCommand()).isEqualTo(StopPumpResult.ABNORMAL_PUMP)
        assertThat(3.codeToStopPumpCommand()).isEqualTo(StopPumpResult.LOW_VOLTAGE)
        assertThat(4.codeToStopPumpCommand()).isEqualTo(StopPumpResult.ABNORMAL_TEMP)
        assertThat(5.codeToStopPumpCommand()).isEqualTo(StopPumpResult.NOT_USED)
        assertThat(12.codeToStopPumpCommand()).isEqualTo(StopPumpResult.PUMP_ERROR)
        assertThat(29.codeToStopPumpCommand()).isEqualTo(StopPumpResult.BY_LGS)
        assertThat(99.codeToStopPumpCommand()).isEqualTo(StopPumpResult.ERROR)
    }

    // ---------------------------------------------------------------------------------------------
    // InfusionModeResult
    // ---------------------------------------------------------------------------------------------

    @Test fun `InfusionModeResult commandToCode maps every value`() {
        assertThat(InfusionModeResult.BASAL.infusionModeToCode()).isEqualTo(1)
        assertThat(InfusionModeResult.TEMP_BASAL.infusionModeToCode()).isEqualTo(2)
        assertThat(InfusionModeResult.IMME_BOLUS.infusionModeToCode()).isEqualTo(3)
        assertThat(InfusionModeResult.EXTEND_IMME_BOLUS.infusionModeToCode()).isEqualTo(4)
        assertThat(InfusionModeResult.EXTEND_BOLUS.infusionModeToCode()).isEqualTo(5)
        assertThat(InfusionModeResult.ERROR.infusionModeToCode()).isEqualTo(-1)
    }

    @Test fun `InfusionModeResult codeToInfusionModeCommand maps codes and default`() {
        assertThat(1.codeToInfusionModeCommand()).isEqualTo(InfusionModeResult.BASAL)
        assertThat(2.codeToInfusionModeCommand()).isEqualTo(InfusionModeResult.TEMP_BASAL)
        assertThat(3.codeToInfusionModeCommand()).isEqualTo(InfusionModeResult.IMME_BOLUS)
        assertThat(4.codeToInfusionModeCommand()).isEqualTo(InfusionModeResult.EXTEND_IMME_BOLUS)
        assertThat(5.codeToInfusionModeCommand()).isEqualTo(InfusionModeResult.EXTEND_BOLUS)
        assertThat(0.codeToInfusionModeCommand()).isEqualTo(InfusionModeResult.ERROR)
        assertThat(99.codeToInfusionModeCommand()).isEqualTo(InfusionModeResult.ERROR)
    }

    // ---------------------------------------------------------------------------------------------
    // InfusionInfoResult
    // ---------------------------------------------------------------------------------------------

    @Test fun `InfusionInfoResult commandToCode maps every value`() {
        assertThat(InfusionInfoResult.BY_REQ.infusionInfoToCode()).isEqualTo(0)
        assertThat(InfusionInfoResult.BY_REMAIN_REQ.infusionInfoToCode()).isEqualTo(1)
        assertThat(InfusionInfoResult.BY_30MIN_RPT.infusionInfoToCode()).isEqualTo(2)
        assertThat(InfusionInfoResult.BY_RECONNECT.infusionInfoToCode()).isEqualTo(3)
        assertThat(InfusionInfoResult.ERROR.infusionInfoToCode()).isEqualTo(-1)
    }

    @Test fun `InfusionInfoResult codeToInfusionInfoCommand maps codes and default`() {
        assertThat(0.codeToInfusionInfoCommand()).isEqualTo(InfusionInfoResult.BY_REQ)
        assertThat(1.codeToInfusionInfoCommand()).isEqualTo(InfusionInfoResult.BY_REMAIN_REQ)
        assertThat(2.codeToInfusionInfoCommand()).isEqualTo(InfusionInfoResult.BY_30MIN_RPT)
        assertThat(3.codeToInfusionInfoCommand()).isEqualTo(InfusionInfoResult.BY_RECONNECT)
        assertThat(42.codeToInfusionInfoCommand()).isEqualTo(InfusionInfoResult.ERROR)
    }

    // ---------------------------------------------------------------------------------------------
    // PumpStateResult (decode receiver is nullable Int?)
    // ---------------------------------------------------------------------------------------------

    @Test fun `PumpStateResult commandToCode maps every value`() {
        assertThat(PumpStateResult.READY.pumpStateToCode()).isEqualTo(0)
        assertThat(PumpStateResult.PRIMING.pumpStateToCode()).isEqualTo(1)
        assertThat(PumpStateResult.RUNNING.pumpStateToCode()).isEqualTo(2)
        assertThat(PumpStateResult.ERROR.pumpStateToCode()).isEqualTo(3)
    }

    @Test fun `PumpStateResult codeToPumpStateCommand maps codes default and null`() {
        assertThat(0.codeToPumpStateCommand()).isEqualTo(PumpStateResult.READY)
        assertThat(1.codeToPumpStateCommand()).isEqualTo(PumpStateResult.PRIMING)
        assertThat(2.codeToPumpStateCommand()).isEqualTo(PumpStateResult.RUNNING)
        assertThat(3.codeToPumpStateCommand()).isEqualTo(PumpStateResult.ERROR)
        assertThat(99.codeToPumpStateCommand()).isEqualTo(PumpStateResult.ERROR)
        val nullCode: Int? = null
        assertThat(nullCode.codeToPumpStateCommand()).isEqualTo(PumpStateResult.ERROR)
    }

    // ---------------------------------------------------------------------------------------------
    // WarningMessageResult
    // ---------------------------------------------------------------------------------------------

    @Test fun `WarningMessageResult commandToCode maps every value`() {
        assertThat(WarningMessageResult.INSULIN_DEFICIENCY.warningToCode()).isEqualTo(1)
        assertThat(WarningMessageResult.EXPIRED.warningToCode()).isEqualTo(2)
        assertThat(WarningMessageResult.LOW_VOLTAGE.warningToCode()).isEqualTo(3)
        assertThat(WarningMessageResult.ABNORMAL_TEMP.warningToCode()).isEqualTo(4)
        assertThat(WarningMessageResult.NOT_USED.warningToCode()).isEqualTo(5)
        assertThat(WarningMessageResult.BLE_CONNECT.warningToCode()).isEqualTo(6)
        assertThat(WarningMessageResult.NOT_STARTED_BASAL.warningToCode()).isEqualTo(7)
        assertThat(WarningMessageResult.EXTENDED_EXPIRED.warningToCode()).isEqualTo(10)
        assertThat(WarningMessageResult.PUMP_ERROR.warningToCode()).isEqualTo(12)
        assertThat(WarningMessageResult.CANNULA_ERROR.warningToCode()).isEqualTo(99)
        assertThat(WarningMessageResult.ERROR.warningToCode()).isEqualTo(-1)
    }

    @Test fun `WarningMessageResult codeToWarningMessageCommand maps codes and default`() {
        assertThat(1.codeToWarningMessageCommand()).isEqualTo(WarningMessageResult.INSULIN_DEFICIENCY)
        assertThat(2.codeToWarningMessageCommand()).isEqualTo(WarningMessageResult.EXPIRED)
        assertThat(3.codeToWarningMessageCommand()).isEqualTo(WarningMessageResult.LOW_VOLTAGE)
        assertThat(4.codeToWarningMessageCommand()).isEqualTo(WarningMessageResult.ABNORMAL_TEMP)
        assertThat(5.codeToWarningMessageCommand()).isEqualTo(WarningMessageResult.NOT_USED)
        assertThat(6.codeToWarningMessageCommand()).isEqualTo(WarningMessageResult.BLE_CONNECT)
        assertThat(7.codeToWarningMessageCommand()).isEqualTo(WarningMessageResult.NOT_STARTED_BASAL)
        assertThat(10.codeToWarningMessageCommand()).isEqualTo(WarningMessageResult.EXTENDED_EXPIRED)
        assertThat(12.codeToWarningMessageCommand()).isEqualTo(WarningMessageResult.PUMP_ERROR)
        assertThat(99.codeToWarningMessageCommand()).isEqualTo(WarningMessageResult.CANNULA_ERROR)
        assertThat(0.codeToWarningMessageCommand()).isEqualTo(WarningMessageResult.ERROR)
    }

    // ---------------------------------------------------------------------------------------------
    // AlertMessageResult
    // ---------------------------------------------------------------------------------------------

    @Test fun `AlertMessageResult commandToCode maps every value`() {
        assertThat(AlertMessageResult.INSULIN_LOW.alertToCode()).isEqualTo(1)
        assertThat(AlertMessageResult.EXPIRED_ALERT.alertToCode()).isEqualTo(2)
        assertThat(AlertMessageResult.BATTERY_EXCEED.alertToCode()).isEqualTo(3)
        assertThat(AlertMessageResult.ABNORMAL_TEMP.alertToCode()).isEqualTo(4)
        assertThat(AlertMessageResult.NOT_USED.alertToCode()).isEqualTo(5)
        assertThat(AlertMessageResult.BLE_CONNECT.alertToCode()).isEqualTo(6)
        assertThat(AlertMessageResult.NOT_START_BASAL.alertToCode()).isEqualTo(7)
        assertThat(AlertMessageResult.PUMP_STOP_FINISH.alertToCode()).isEqualTo(8)
        assertThat(AlertMessageResult.EXTEND_EXPIRED.alertToCode()).isEqualTo(10)
        assertThat(AlertMessageResult.ERROR.alertToCode()).isEqualTo(-1)
    }

    @Test fun `AlertMessageResult codeToAlertMessageCommand maps codes and default`() {
        assertThat(1.codeToAlertMessageCommand()).isEqualTo(AlertMessageResult.INSULIN_LOW)
        assertThat(2.codeToAlertMessageCommand()).isEqualTo(AlertMessageResult.EXPIRED_ALERT)
        assertThat(3.codeToAlertMessageCommand()).isEqualTo(AlertMessageResult.BATTERY_EXCEED)
        assertThat(4.codeToAlertMessageCommand()).isEqualTo(AlertMessageResult.ABNORMAL_TEMP)
        assertThat(5.codeToAlertMessageCommand()).isEqualTo(AlertMessageResult.NOT_USED)
        assertThat(6.codeToAlertMessageCommand()).isEqualTo(AlertMessageResult.BLE_CONNECT)
        assertThat(7.codeToAlertMessageCommand()).isEqualTo(AlertMessageResult.NOT_START_BASAL)
        assertThat(8.codeToAlertMessageCommand()).isEqualTo(AlertMessageResult.PUMP_STOP_FINISH)
        assertThat(10.codeToAlertMessageCommand()).isEqualTo(AlertMessageResult.EXTEND_EXPIRED)
        assertThat(0.codeToAlertMessageCommand()).isEqualTo(AlertMessageResult.ERROR)
    }

    // ---------------------------------------------------------------------------------------------
    // NoticeMessageResult
    // ---------------------------------------------------------------------------------------------

    @Test fun `NoticeMessageResult commandToCode maps every value`() {
        assertThat(NoticeMessageResult.REMAIN_EXCEED.noticeToCode()).isEqualTo(1)
        assertThat(NoticeMessageResult.EXPIRED_NOTICE.noticeToCode()).isEqualTo(2)
        assertThat(NoticeMessageResult.INSPECTING.noticeToCode()).isEqualTo(3)
        assertThat(NoticeMessageResult.SYNC_TIME.noticeToCode()).isEqualTo(26)
        assertThat(NoticeMessageResult.GLUCOSE.noticeToCode()).isEqualTo(27)
        assertThat(NoticeMessageResult.ERROR.noticeToCode()).isEqualTo(-1)
    }

    @Test fun `NoticeMessageResult codeToNoticeMessageCommand maps codes and default`() {
        assertThat(1.codeToNoticeMessageCommand()).isEqualTo(NoticeMessageResult.REMAIN_EXCEED)
        assertThat(2.codeToNoticeMessageCommand()).isEqualTo(NoticeMessageResult.EXPIRED_NOTICE)
        assertThat(3.codeToNoticeMessageCommand()).isEqualTo(NoticeMessageResult.INSPECTING)
        assertThat(26.codeToNoticeMessageCommand()).isEqualTo(NoticeMessageResult.SYNC_TIME)
        assertThat(27.codeToNoticeMessageCommand()).isEqualTo(NoticeMessageResult.GLUCOSE)
        assertThat(0.codeToNoticeMessageCommand()).isEqualTo(NoticeMessageResult.ERROR)
    }

    // ---------------------------------------------------------------------------------------------
    // Protocol classification
    // ---------------------------------------------------------------------------------------------

    @Test fun `isPatchProtocol true false and default branches`() {
        // representative "patch == true" opcodes
        assertThat(isPatchProtocol(0x11)).isTrue()
        assertThat(isPatchProtocol(0x26)).isTrue()
        assertThat(isPatchProtocol(0x31)).isTrue()
        assertThat(isPatchProtocol(0x4A)).isTrue()
        assertThat(isPatchProtocol(0xBB)).isTrue()
        // explicitly listed "false" opcodes
        assertThat(isPatchProtocol(0x13)).isFalse()
        assertThat(isPatchProtocol(0x21)).isFalse()
        assertThat(isPatchProtocol(0x9C)).isFalse()
        // unlisted -> else -> false
        assertThat(isPatchProtocol(0x00)).isFalse()
        assertThat(isPatchProtocol(0xFF)).isFalse()
    }

    @Test fun `isBasalProtocol true false and default branches`() {
        assertThat(isBasalProtocol(0x13)).isTrue()
        assertThat(isBasalProtocol(0x21)).isTrue()
        assertThat(isBasalProtocol(0x88)).isTrue()
        assertThat(isBasalProtocol(0x2D)).isTrue()
        assertThat(isBasalProtocol(0x11)).isFalse()
        assertThat(isBasalProtocol(0x24)).isFalse()
        assertThat(isBasalProtocol(0x00)).isFalse()
    }

    @Test fun `isBolusProtocol true false and default branches`() {
        assertThat(isBolusProtocol(0x24)).isTrue()
        assertThat(isBolusProtocol(0x29)).isTrue()
        assertThat(isBolusProtocol(0x2C)).isTrue()
        assertThat(isBolusProtocol(0x85)).isTrue()
        assertThat(isBolusProtocol(0x11)).isFalse()
        assertThat(isBolusProtocol(0x13)).isFalse()
        assertThat(isBolusProtocol(0x00)).isFalse()
    }

    // ---------------------------------------------------------------------------------------------
    // createPatchResultModel — one assertion per response branch
    // ---------------------------------------------------------------------------------------------

    @Test fun `patch SetTimeResponse maps to SetTimeResultModel`() {
        assertThat(createPatchResultModel(SetTimeResponse(ts, patchCmd, 1)))
            .isEqualTo(SetTimeResultModel(Result.FAILED))
    }

    @Test fun `patch PatchInformationInquiryResponse maps model`() {
        assertThat(createPatchResultModel(PatchInformationInquiryResponse(ts, patchCmd, 0, "SN123")))
            .isEqualTo(PatchInformationInquiryModel(Result.SUCCESS, "SN123"))
    }

    @Test fun `patch PatchInformationInquiryDetailResponse maps model`() {
        assertThat(createPatchResultModel(PatchInformationInquiryDetailResponse(ts, patchCmd, 0, "1.2.3", "2024-01-01", "CL-100")))
            .isEqualTo(PatchInformationInquiryDetailModel(Result.SUCCESS, "1.2.3", "2024-01-01", "CL-100"))
    }

    @Test fun `patch SafetyCheckResponse maps model`() {
        assertThat(createPatchResultModel(SafetyCheckResponse(ts, patchCmd, 1, 120, 30)))
            .isEqualTo(SafetyCheckResultModel(SafetyCheckResult.INSULIN_DEFICIENCY, 120, 30))
    }

    @Test fun `patch ThresholdSetResponse maps model`() {
        assertThat(createPatchResultModel(ThresholdSetResponse(ts, patchCmd, 0)))
            .isEqualTo(ThresholdSetResultModel(Result.SUCCESS))
    }

    @Test fun `patch CannulaInsertionResponse maps model`() {
        assertThat(createPatchResultModel(CannulaInsertionResponse(ts, patchCmd, 0)))
            .isEqualTo(CannulaInsertionResultModel(Result.SUCCESS))
    }

    @Test fun `patch CannulaInsertionAckResponse maps model`() {
        assertThat(createPatchResultModel(CannulaInsertionAckResponse(ts, patchCmd, 0)))
            .isEqualTo(CannulaInsertionAckResultModel(Result.SUCCESS))
    }

    @Test fun `patch SetInfusionThresholdResponse maps model`() {
        assertThat(createPatchResultModel(SetInfusionThresholdResponse(ts, patchCmd, 2, 0)))
            .isEqualTo(SetInfusionThresholdResultModel(Result.SUCCESS, 2))
    }

    @Test fun `patch SetBuzzModeResponse maps model`() {
        assertThat(createPatchResultModel(SetBuzzModeResponse(ts, patchCmd, 0)))
            .isEqualTo(SetBuzzModeResultModel(Result.SUCCESS))
    }

    @Test fun `patch ClearReportResponse maps to SetAlarmClearResultModel`() {
        assertThat(createPatchResultModel(ClearReportResponse(ts, patchCmd, 0, 3, 4)))
            .isEqualTo(SetAlarmClearResultModel(Result.SUCCESS, 3, 4))
    }

    @Test fun `patch SetExpiryExtendResponse maps to ExtendPatchExpiryResultModel`() {
        assertThat(createPatchResultModel(SetExpiryExtendResponse(ts, patchCmd, 0)))
            .isEqualTo(ExtendPatchExpiryResultModel(Result.SUCCESS))
    }

    @Test fun `patch StopPumpResponse maps model`() {
        assertThat(createPatchResultModel(StopPumpResponse(ts, patchCmd, 0)))
            .isEqualTo(StopPumpResultModel(Result.SUCCESS))
    }

    @Test fun `patch ResumePumpResponse maps model`() {
        assertThat(createPatchResultModel(ResumePumpResponse(ts, patchCmd, 0, 1, 7)))
            .isEqualTo(ResumePumpResultModel(StopPumpResult.BY_REQ, InfusionModeResult.BASAL, 7))
    }

    @Test fun `patch StopPumpReportResponse maps model`() {
        assertThat(createPatchResultModel(StopPumpReportResponse(ts, patchCmd, 1, 3, 8, 1.5, 0.5, 25)))
            .isEqualTo(StopPumpReportResultModel(StopPumpResult.INSULIN_DEFICIENCY, InfusionModeResult.IMME_BOLUS, 8, 1.5, 0.5, 25))
    }

    @Test fun `patch RetrieveInfusionStatusResponse maps model`() {
        assertThat(
            createPatchResultModel(
                RetrieveInfusionStatusResponse(ts, patchCmd, 0, 30, 100.0, 1.0, 2.0, 2, 1, 60, 5.0, 45)
            )
        ).isEqualTo(
            InfusionInfoReportResultModel(
                InfusionInfoResult.BY_REQ, 30, 100.0, 1.0, 2.0, PumpStateResult.RUNNING, InfusionModeResult.BASAL, 60, 5.0, 45
            )
        )
    }

    @Test fun `patch SetApplicationStatusResponse maps model`() {
        assertThat(createPatchResultModel(SetApplicationStatusResponse(ts, patchCmd, 1)))
            .isEqualTo(SetApplicationStatusResultModel(1))
    }

    @Test fun `patch RetrieveAddressResponse maps model`() {
        assertThat(createPatchResultModel(RetrieveAddressResponse(ts, patchCmd, "AA:BB:CC", "3F")))
            .isEqualTo(RetrieveAddressResultModel("AA:BB:CC", "3F"))
    }

    @Test fun `patch SetDiscardResponse maps to DiscardPatchResultModel`() {
        assertThat(createPatchResultModel(SetDiscardResponse(ts, patchCmd, 0)))
            .isEqualTo(DiscardPatchResultModel(Result.SUCCESS))
    }

    @Test fun `patch RecoveryPatchResponse maps to RecoveryPatchReportResultModel`() {
        assertThat(createPatchResultModel(RecoveryPatchResponse(ts, patchCmd)))
            .isInstanceOf(RecoveryPatchReportResultModel::class.java)
    }

    @Test fun `patch WarningReportResponse resolves AlarmCause`() {
        assertThat(createPatchResultModel(WarningReportResponse(ts, patchCmd, 0x01, 50)))
            .isEqualTo(WarningReportResultModel(AlarmCause.ALARM_WARNING_LOW_INSULIN, 50))
    }

    @Test fun `patch WarningReportResponse unknown cause maps to ALARM_UNKNOWN`() {
        assertThat(createPatchResultModel(WarningReportResponse(ts, patchCmd, 0x7F, 0)))
            .isEqualTo(WarningReportResultModel(AlarmCause.ALARM_UNKNOWN, 0))
    }

    @Test fun `patch AlertReportResponse resolves AlarmCause`() {
        assertThat(createPatchResultModel(AlertReportResponse(ts, patchCmd, 0x01, 60)))
            .isEqualTo(AlertReportResultModel(AlarmCause.ALARM_ALERT_OUT_OF_INSULIN, 60))
    }

    @Test fun `patch NoticeReportResponse resolves AlarmCause`() {
        assertThat(createPatchResultModel(NoticeReportResponse(ts, patchCmd, 0x01, 70)))
            .isEqualTo(NoticeReportResultModel(AlarmCause.ALARM_NOTICE_LOW_INSULIN, 70))
    }

    @Test fun `patch AppAuthRptResponse maps model`() {
        assertThat(createPatchResultModel(AppAuthRptResponse(ts, patchCmd, 5)))
            .isEqualTo(AppAuthAckReportResultModel(5))
    }

    @Test fun `patch AdditionalPrimingResponse maps model`() {
        assertThat(createPatchResultModel(AdditionalPrimingResponse(ts, patchCmd, 0)))
            .isEqualTo(AdditionalPrimingResultModel(Result.SUCCESS))
    }

    @Test fun `patch SetThresholdNoticeResponse maps model`() {
        assertThat(createPatchResultModel(SetThresholdNoticeResponse(ts, patchCmd, 0, 1)))
            .isEqualTo(SetThresholdNoticeResultModel(1, Result.SUCCESS))
    }

    @Test fun `patch SetAlertAlarmModelResponse maps to AlertAlarmSetResultModel`() {
        assertThat(createPatchResultModel(SetAlertAlarmModelResponse(ts, patchCmd, 0)))
            .isEqualTo(AlertAlarmSetResultModel(Result.SUCCESS))
    }

    @Test fun `patch AppAuthAckRptResponse maps to AppAuthAckResultModel`() {
        assertThat(createPatchResultModel(AppAuthAckRptResponse(ts, patchCmd, 0)))
            .isEqualTo(AppAuthAckResultModel(Result.SUCCESS))
    }

    @Test fun `patch AppAlarmOffResponse maps to AppAlarmClearResultModel`() {
        assertThat(createPatchResultModel(AppAlarmOffResponse(ts, patchCmd, 0)))
            .isEqualTo(AppAlarmClearResultModel(Result.SUCCESS))
    }

    @Test fun `patch RetrieveOperationInfoResponse maps model`() {
        assertThat(createPatchResultModel(RetrieveOperationInfoResponse(ts, patchCmd, 2, 10, 100, 5, 30, 50.0)))
            .isEqualTo(RetrieveOperationInfoResultModel(2, 10, 100, 5, 30, 50.0))
    }

    @Test fun `patch CheckBuzzResponse maps to AppBuzzResultModel`() {
        assertThat(createPatchResultModel(CheckBuzzResponse(ts, patchCmd, 0)))
            .isEqualTo(AppBuzzResultModel(Result.SUCCESS))
    }

    @Test fun `patch unrecognised response type returns null`() {
        assertThat(createPatchResultModel(SetInitializeResponse(ts, patchCmd, 1))).isNull()
    }

    @Test fun `patch non-patch command returns null even for known type`() {
        assertThat(createPatchResultModel(SetTimeResponse(ts, basalCmd, 0))).isNull()
    }

    // ---------------------------------------------------------------------------------------------
    // createBasalResultModel
    // ---------------------------------------------------------------------------------------------

    @Test fun `basal SetBasalProgramResponse maps model`() {
        assertThat(createBasalResultModel(SetBasalProgramResponse(ts, basalCmd, 0)))
            .isEqualTo(SetBasalProgramResultModel(SetBasalProgramResult.SUCCESS))
    }

    @Test fun `basal SetBasalProgramAdditionalResponse maps model`() {
        assertThat(createBasalResultModel(SetBasalProgramAdditionalResponse(ts, basalCmd, 1)))
            .isEqualTo(SetBasalProgramAdditionalResultModel(SetBasalProgramResult.INSULIN_DEFICIENCY))
    }

    @Test fun `basal UpdateBasalProgramResponse maps model`() {
        assertThat(createBasalResultModel(UpdateBasalProgramResponse(ts, basalCmd, 0)))
            .isEqualTo(UpdateBasalProgramResultModel(SetBasalProgramResult.SUCCESS))
    }

    @Test fun `basal UpdateBasalProgramAdditionalResponse maps model`() {
        assertThat(createBasalResultModel(UpdateBasalProgramAdditionalResponse(ts, basalCmd, 0)))
            .isEqualTo(UpdateBasalProgramAdditionalResultModel(SetBasalProgramResult.SUCCESS))
    }

    @Test fun `basal StartTempBasalProgramResponse maps model`() {
        assertThat(createBasalResultModel(StartTempBasalProgramResponse(ts, basalCmd, 20)))
            .isEqualTo(StartTempBasalProgramResultModel(SetBasalProgramResult.EXCEED_LIMIT))
    }

    @Test fun `basal CancelTempBasalProgramResponse maps model`() {
        assertThat(createBasalResultModel(CancelTempBasalProgramResponse(ts, basalCmd, 0)))
            .isEqualTo(CancelTempBasalProgramResultModel(Result.SUCCESS))
    }

    @Test fun `basal StartBasalProgramResponse maps to StartBasalProgramResultModel`() {
        assertThat(createBasalResultModel(StartBasalProgramResponse(ts, basalCmd)))
            .isInstanceOf(StartBasalProgramResultModel::class.java)
    }

    @Test fun `basal ResumeBasalProgramResponse maps model`() {
        assertThat(createBasalResultModel(ResumeBasalProgramResponse(ts, basalCmd, 1, 2.5, 30, 100.0)))
            .isEqualTo(BasalInfusionResumeResultModel(1, 2.5, 30, 100.0))
    }

    @Test fun `basal unrecognised response type returns null`() {
        assertThat(createBasalResultModel(SetInitializeResponse(ts, basalCmd, 1))).isNull()
    }

    @Test fun `basal non-basal command returns null even for known type`() {
        assertThat(createBasalResultModel(SetBasalProgramResponse(ts, patchCmd, 0))).isNull()
    }

    // ---------------------------------------------------------------------------------------------
    // createBolusResultModel
    // ---------------------------------------------------------------------------------------------

    @Test fun `bolus StartImmeBolusResponse maps model`() {
        assertThat(createBolusResultModel(StartImmeBolusResponse(ts, bolusCmd, 0, 11, 60, 99.0)))
            .isEqualTo(StartImmeBolusResultModel(SetBolusProgramResult.SUCCESS, 11, 60, 99.0))
    }

    @Test fun `bolus CancelImmeBolusResponse maps model`() {
        assertThat(createBolusResultModel(CancelImmeBolusResponse(ts, bolusCmd, 0, 50.0, 2.0)))
            .isEqualTo(CancelImmeBolusResultModel(Result.SUCCESS, 50.0, 2.0))
    }

    @Test fun `bolus StartExtendBolusResponse maps model`() {
        assertThat(createBolusResultModel(StartExtendBolusResponse(ts, bolusCmd, 0, 120)))
            .isEqualTo(StartExtendBolusResultModel(SetBolusProgramResult.SUCCESS, 120))
    }

    @Test fun `bolus CancelExtendBolusResponse maps model`() {
        assertThat(createBolusResultModel(CancelExtendBolusResponse(ts, bolusCmd, 0, 1.0)))
            .isEqualTo(CancelExtendBolusResultModel(Result.SUCCESS, 1.0))
    }

    @Test fun `bolus DelayExtendBolusResponse maps model`() {
        assertThat(createBolusResultModel(DelayExtendBolusResponse(ts, bolusCmd, 3.0, 90)))
            .isEqualTo(DelayExtendBolusReportResultModel(3.0, 90))
    }

    @Test fun `bolus unrecognised response type returns null`() {
        assertThat(createBolusResultModel(SetInitializeResponse(ts, bolusCmd, 1))).isNull()
    }

    @Test fun `bolus non-bolus command returns null even for known type`() {
        assertThat(createBolusResultModel(StartImmeBolusResponse(ts, patchCmd, 0, 1, 60, 99.0))).isNull()
    }

    // ---------------------------------------------------------------------------------------------
    // Result-model DTOs not produced by any create* dispatcher (constructor coverage)
    // ---------------------------------------------------------------------------------------------

    @Test fun `standalone result-model DTOs construct correctly`() {
        assertThat(ProtocolFailedAlarmMode(alarmId = 42L, cause = 7).cause).isEqualTo(7)
        assertThat(FinishPulseReportResultModel(InfusionModeResult.EXTEND_BOLUS, 3, 10, 2, 15, 40.0).remains).isEqualTo(40.0)
        assertThat(SetBasalProgramResultModel(SetBasalProgramResult.SUCCESS).result).isEqualTo(SetBasalProgramResult.SUCCESS)
        assertThat(StartTempBasalProgramResultModel(SetBasalProgramResult.EXPIRED).result).isEqualTo(SetBasalProgramResult.EXPIRED)
    }

    // ---------------------------------------------------------------------------------------------
    // Request DTO construction
    // ---------------------------------------------------------------------------------------------

    @Test fun `simple request DTOs carry their fields`() {
        assertThat(SetTimeRequest("2024-01-01T00:00:00", 100, 1, 2).aidMode).isEqualTo(2)
        assertThat(SetBuzzModeRequest(true).isOn).isTrue()
        assertThat(ThresholdSetRequest(100, 116, 2.5, 15.0, false).buzzUse).isFalse()
        assertThat(SetAlertAlarmModeRequest(3).mode).isEqualTo(3)
        assertThat(SetExpiryExtendRequest(24).extendHour).isEqualTo(24)
        assertThat(StopPumpRequest(30, 4).expectMinutes).isEqualTo(30)
        assertThat(ResumePumpRequest(1, 7).causeId).isEqualTo(7)
        assertThat(StopPumpRptAckRequest(5).subId).isEqualTo(5)
        assertThat(SetThresholdInfusionMaxSpeedRequest(12.5).value).isEqualTo(12.5)
        assertThat(SetThresholdNoticeRequest(50, 2).type).isEqualTo(2)
        assertThat(SetThresholdInfusionMaxDoseRequest(20.0).value).isEqualTo(20.0)
        assertThat(RetrieveInfusionStatusRequest(1).inquiryType).isEqualTo(1)
        assertThat(SetApplicationStatusRequest(true, 6).infusionStopHour).isEqualTo(6)
        assertThat(SetAlarmClearRequest(0, 9).causeId).isEqualTo(9)
        assertThat(SetInitializeRequest(true).mode).isTrue()
        assertThat(RetrieveAddressRequest(0x0A.toByte()).key).isEqualTo(0x0A.toByte())
    }

    @Test fun `basal request DTOs carry their segments`() {
        val segments = listOf(CarelevoBasalSegment(0, 0, 1.5), CarelevoBasalSegment(1, 30, 2.0))
        assertThat(SetBasalProgramRequest(2, segments).segmentList).isEqualTo(segments)
        assertThat(SetBasalProgramAdditionalRequest(1, 2, segments).msgNumber).isEqualTo(1)
        assertThat(SetBasalProgramRequestV2(3, segments).seqNo).isEqualTo(3)
        assertThat(UpdateBasalProgramRequest(2, segments).totalBasalSegmentCnt).isEqualTo(2)
        assertThat(UpdateBasalProgramAdditionalRequest(1, 2, segments).segmentCnt).isEqualTo(2)
    }

    @Test fun `temp-basal and bolus request DTOs carry their fields`() {
        assertThat(StartTempBasalProgramByUnitRequest(1.5, 1, 30).infusionUnit).isEqualTo(1.5)
        assertThat(StartTempBasalProgramByPercentRequest(150, 2, 0).infusionPercent).isEqualTo(150)
        assertThat(StartImmeBolusRequest(7, 3.5).volume).isEqualTo(3.5)
        assertThat(StartExtendBolusRequest(4.0, 1.0, 1, 30).hour).isEqualTo(1)
    }
}
