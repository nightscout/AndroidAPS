package app.aaps.pump.carelevo.domain.model.bt

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
import app.aaps.pump.carelevo.domain.type.AlarmType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure-logic coverage for the Carelevo BT result layer:
 *  - the enum companion parse/encode factories in CarelevoBtEnums.kt
 *  - the response -> result-model dispatch factories in CarelevoBtModel.kt
 *  - the protocol classifiers in CarelevoProtocolChecker.kt
 * Everything under test is plain, mockable JVM logic (no Android, no coroutines).
 */
internal class CarelevoBtResultTest {

    private companion object {

        const val TS = 1_000L

        // Command opcodes chosen so the dispatch guards resolve to the intended protocol family.
        const val PATCH = 0x11   // isPatchProtocol == true
        const val BASAL = 0x13   // isBasalProtocol == true
        const val BOLUS = 0x24   // isBolusProtocol == true
    }

    // region Result enum ---------------------------------------------------------------------------

    @Test fun `Result codeToResultCommand maps known codes`() {
        assertThat(0.codeToResultCommand()).isEqualTo(Result.SUCCESS)
        assertThat(1.codeToResultCommand()).isEqualTo(Result.FAILED)
    }

    @Test fun `Result codeToResultCommand falls back to FAILED for unknown`() {
        assertThat(2.codeToResultCommand()).isEqualTo(Result.FAILED)
        assertThat((-1).codeToResultCommand()).isEqualTo(Result.FAILED)
    }

    @Test fun `Result commandToCode encodes both members`() {
        assertThat(Result.SUCCESS.resultToCode()).isEqualTo(0)
        assertThat(Result.FAILED.resultToCode()).isEqualTo(1)
    }

    // endregion

    // region SafetyCheckResult enum ----------------------------------------------------------------

    @Test fun `SafetyCheckResult codeToSafetyCheckCommand maps every known code`() {
        assertThat(0.codeToSafetyCheckCommand()).isEqualTo(SafetyCheckResult.SUCCESS)
        assertThat(1.codeToSafetyCheckCommand()).isEqualTo(SafetyCheckResult.INSULIN_DEFICIENCY)
        assertThat(2.codeToSafetyCheckCommand()).isEqualTo(SafetyCheckResult.EXPIRED)
        assertThat(3.codeToSafetyCheckCommand()).isEqualTo(SafetyCheckResult.LOW_VOLTAGE)
        assertThat(11.codeToSafetyCheckCommand()).isEqualTo(SafetyCheckResult.PATCH_ERROR)
        assertThat(12.codeToSafetyCheckCommand()).isEqualTo(SafetyCheckResult.PUMP_ERROR)
        assertThat(4.codeToSafetyCheckCommand()).isEqualTo(SafetyCheckResult.REP_REQUEST)
        assertThat(18.codeToSafetyCheckCommand()).isEqualTo(SafetyCheckResult.REP_REQUEST1)
    }

    @Test fun `SafetyCheckResult codeToSafetyCheckCommand unknown is FAILED`() {
        assertThat(99.codeToSafetyCheckCommand()).isEqualTo(SafetyCheckResult.FAILED)
    }

    @Test fun `SafetyCheckResult commandToCode encodes every member`() {
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

    // endregion

    // region SetBasalProgramResult enum ------------------------------------------------------------

    @Test fun `SetBasalProgramResult codeToSetBasalProgramCommand maps every known code`() {
        assertThat(0.codeToSetBasalProgramCommand()).isEqualTo(SetBasalProgramResult.SUCCESS)
        assertThat(1.codeToSetBasalProgramCommand()).isEqualTo(SetBasalProgramResult.INSULIN_DEFICIENCY)
        assertThat(2.codeToSetBasalProgramCommand()).isEqualTo(SetBasalProgramResult.EXPIRED)
        assertThat(3.codeToSetBasalProgramCommand()).isEqualTo(SetBasalProgramResult.LOW_VOLTAGE)
        assertThat(4.codeToSetBasalProgramCommand()).isEqualTo(SetBasalProgramResult.ABNORMAL_TEMP)
        assertThat(12.codeToSetBasalProgramCommand()).isEqualTo(SetBasalProgramResult.PUMP_ERROR)
        assertThat(19.codeToSetBasalProgramCommand()).isEqualTo(SetBasalProgramResult.ABNORMAL_PROGRAM)
        assertThat(20.codeToSetBasalProgramCommand()).isEqualTo(SetBasalProgramResult.EXCEED_LIMIT)
    }

    @Test fun `SetBasalProgramResult codeToSetBasalProgramCommand unknown is FAILED`() {
        assertThat(255.codeToSetBasalProgramCommand()).isEqualTo(SetBasalProgramResult.FAILED)
    }

    @Test fun `SetBasalProgramResult commandToCode encodes every member`() {
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

    // endregion

    // region SetBolusProgramResult enum ------------------------------------------------------------

    @Test fun `SetBolusProgramResult codeToSetBolusProgramCommand maps every known code`() {
        assertThat(0.codeToSetBolusProgramCommand()).isEqualTo(SetBolusProgramResult.SUCCESS)
        assertThat(1.codeToSetBolusProgramCommand()).isEqualTo(SetBolusProgramResult.INSULIN_DEFICIENCY)
        assertThat(2.codeToSetBolusProgramCommand()).isEqualTo(SetBolusProgramResult.EXPIRED)
        assertThat(3.codeToSetBolusProgramCommand()).isEqualTo(SetBolusProgramResult.LOW_VOLTAGE)
        assertThat(4.codeToSetBolusProgramCommand()).isEqualTo(SetBolusProgramResult.ABNORMAL_TEMP)
        assertThat(12.codeToSetBolusProgramCommand()).isEqualTo(SetBolusProgramResult.PUMP_ERROR)
        assertThat(20.codeToSetBolusProgramCommand()).isEqualTo(SetBolusProgramResult.EXCEED_LIMIT)
    }

    @Test fun `SetBolusProgramResult codeToSetBolusProgramCommand unknown is FAILED`() {
        assertThat(7.codeToSetBolusProgramCommand()).isEqualTo(SetBolusProgramResult.FAILED)
    }

    @Test fun `SetBolusProgramResult commandToCode encodes every member`() {
        assertThat(SetBolusProgramResult.SUCCESS.setBolusToCode()).isEqualTo(0)
        assertThat(SetBolusProgramResult.INSULIN_DEFICIENCY.setBolusToCode()).isEqualTo(1)
        assertThat(SetBolusProgramResult.EXPIRED.setBolusToCode()).isEqualTo(2)
        assertThat(SetBolusProgramResult.LOW_VOLTAGE.setBolusToCode()).isEqualTo(3)
        assertThat(SetBolusProgramResult.ABNORMAL_TEMP.setBolusToCode()).isEqualTo(4)
        assertThat(SetBolusProgramResult.PUMP_ERROR.setBolusToCode()).isEqualTo(12)
        assertThat(SetBolusProgramResult.EXCEED_LIMIT.setBolusToCode()).isEqualTo(20)
        assertThat(SetBolusProgramResult.FAILED.setBolusToCode()).isEqualTo(-1)
    }

    // endregion

    // region StopPumpResult enum -------------------------------------------------------------------

    @Test fun `StopPumpResult codeToStopPumpCommand maps every known code`() {
        assertThat(0.codeToStopPumpCommand()).isEqualTo(StopPumpResult.BY_REQ)
        assertThat(1.codeToStopPumpCommand()).isEqualTo(StopPumpResult.INSULIN_DEFICIENCY)
        assertThat(2.codeToStopPumpCommand()).isEqualTo(StopPumpResult.ABNORMAL_PUMP)
        assertThat(3.codeToStopPumpCommand()).isEqualTo(StopPumpResult.LOW_VOLTAGE)
        assertThat(4.codeToStopPumpCommand()).isEqualTo(StopPumpResult.ABNORMAL_TEMP)
        assertThat(5.codeToStopPumpCommand()).isEqualTo(StopPumpResult.NOT_USED)
        assertThat(12.codeToStopPumpCommand()).isEqualTo(StopPumpResult.PUMP_ERROR)
        assertThat(29.codeToStopPumpCommand()).isEqualTo(StopPumpResult.BY_LGS)
    }

    @Test fun `StopPumpResult codeToStopPumpCommand unknown is ERROR`() {
        assertThat(77.codeToStopPumpCommand()).isEqualTo(StopPumpResult.ERROR)
    }

    @Test fun `StopPumpResult commandToCode encodes every member`() {
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

    // endregion

    // region InfusionModeResult enum ---------------------------------------------------------------

    @Test fun `InfusionModeResult codeToInfusionModeCommand maps every known code`() {
        assertThat(1.codeToInfusionModeCommand()).isEqualTo(InfusionModeResult.BASAL)
        assertThat(2.codeToInfusionModeCommand()).isEqualTo(InfusionModeResult.TEMP_BASAL)
        assertThat(3.codeToInfusionModeCommand()).isEqualTo(InfusionModeResult.IMME_BOLUS)
        assertThat(4.codeToInfusionModeCommand()).isEqualTo(InfusionModeResult.EXTEND_IMME_BOLUS)
        assertThat(5.codeToInfusionModeCommand()).isEqualTo(InfusionModeResult.EXTEND_BOLUS)
    }

    @Test fun `InfusionModeResult codeToInfusionModeCommand unknown is ERROR`() {
        assertThat(0.codeToInfusionModeCommand()).isEqualTo(InfusionModeResult.ERROR)
        assertThat(6.codeToInfusionModeCommand()).isEqualTo(InfusionModeResult.ERROR)
    }

    @Test fun `InfusionModeResult commandToCode encodes every member`() {
        assertThat(InfusionModeResult.BASAL.infusionModeToCode()).isEqualTo(1)
        assertThat(InfusionModeResult.TEMP_BASAL.infusionModeToCode()).isEqualTo(2)
        assertThat(InfusionModeResult.IMME_BOLUS.infusionModeToCode()).isEqualTo(3)
        assertThat(InfusionModeResult.EXTEND_IMME_BOLUS.infusionModeToCode()).isEqualTo(4)
        assertThat(InfusionModeResult.EXTEND_BOLUS.infusionModeToCode()).isEqualTo(5)
        assertThat(InfusionModeResult.ERROR.infusionModeToCode()).isEqualTo(-1)
    }

    // endregion

    // region InfusionInfoResult enum ---------------------------------------------------------------

    @Test fun `InfusionInfoResult codeToInfusionInfoCommand maps every known code`() {
        assertThat(0.codeToInfusionInfoCommand()).isEqualTo(InfusionInfoResult.BY_REQ)
        assertThat(1.codeToInfusionInfoCommand()).isEqualTo(InfusionInfoResult.BY_REMAIN_REQ)
        assertThat(2.codeToInfusionInfoCommand()).isEqualTo(InfusionInfoResult.BY_30MIN_RPT)
        assertThat(3.codeToInfusionInfoCommand()).isEqualTo(InfusionInfoResult.BY_RECONNECT)
    }

    @Test fun `InfusionInfoResult codeToInfusionInfoCommand unknown is ERROR`() {
        assertThat(9.codeToInfusionInfoCommand()).isEqualTo(InfusionInfoResult.ERROR)
    }

    @Test fun `InfusionInfoResult commandToCode encodes every member`() {
        assertThat(InfusionInfoResult.BY_REQ.infusionInfoToCode()).isEqualTo(0)
        assertThat(InfusionInfoResult.BY_REMAIN_REQ.infusionInfoToCode()).isEqualTo(1)
        assertThat(InfusionInfoResult.BY_30MIN_RPT.infusionInfoToCode()).isEqualTo(2)
        assertThat(InfusionInfoResult.BY_RECONNECT.infusionInfoToCode()).isEqualTo(3)
        assertThat(InfusionInfoResult.ERROR.infusionInfoToCode()).isEqualTo(-1)
    }

    // endregion

    // region PumpStateResult enum ------------------------------------------------------------------

    @Test fun `PumpStateResult codeToPumpStateCommand maps every known code`() {
        assertThat(0.codeToPumpStateCommand()).isEqualTo(PumpStateResult.READY)
        assertThat(1.codeToPumpStateCommand()).isEqualTo(PumpStateResult.PRIMING)
        assertThat(2.codeToPumpStateCommand()).isEqualTo(PumpStateResult.RUNNING)
    }

    @Test fun `PumpStateResult codeToPumpStateCommand unknown or null is ERROR`() {
        assertThat(3.codeToPumpStateCommand()).isEqualTo(PumpStateResult.ERROR)
        val nullCode: Int? = null
        assertThat(nullCode.codeToPumpStateCommand()).isEqualTo(PumpStateResult.ERROR)
    }

    @Test fun `PumpStateResult commandToCode encodes every member`() {
        assertThat(PumpStateResult.READY.pumpStateToCode()).isEqualTo(0)
        assertThat(PumpStateResult.PRIMING.pumpStateToCode()).isEqualTo(1)
        assertThat(PumpStateResult.RUNNING.pumpStateToCode()).isEqualTo(2)
        assertThat(PumpStateResult.ERROR.pumpStateToCode()).isEqualTo(3)
    }

    // endregion

    // region WarningMessageResult enum -------------------------------------------------------------

    @Test fun `WarningMessageResult codeToWarningMessageCommand maps every known code`() {
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
    }

    @Test fun `WarningMessageResult codeToWarningMessageCommand unknown is ERROR`() {
        assertThat(50.codeToWarningMessageCommand()).isEqualTo(WarningMessageResult.ERROR)
    }

    @Test fun `WarningMessageResult commandToCode encodes every member`() {
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

    // endregion

    // region AlertMessageResult enum ---------------------------------------------------------------

    @Test fun `AlertMessageResult codeToAlertMessageCommand maps every known code`() {
        assertThat(1.codeToAlertMessageCommand()).isEqualTo(AlertMessageResult.INSULIN_LOW)
        assertThat(2.codeToAlertMessageCommand()).isEqualTo(AlertMessageResult.EXPIRED_ALERT)
        assertThat(3.codeToAlertMessageCommand()).isEqualTo(AlertMessageResult.BATTERY_EXCEED)
        assertThat(4.codeToAlertMessageCommand()).isEqualTo(AlertMessageResult.ABNORMAL_TEMP)
        assertThat(5.codeToAlertMessageCommand()).isEqualTo(AlertMessageResult.NOT_USED)
        assertThat(6.codeToAlertMessageCommand()).isEqualTo(AlertMessageResult.BLE_CONNECT)
        assertThat(7.codeToAlertMessageCommand()).isEqualTo(AlertMessageResult.NOT_START_BASAL)
        assertThat(8.codeToAlertMessageCommand()).isEqualTo(AlertMessageResult.PUMP_STOP_FINISH)
        assertThat(10.codeToAlertMessageCommand()).isEqualTo(AlertMessageResult.EXTEND_EXPIRED)
    }

    @Test fun `AlertMessageResult codeToAlertMessageCommand unknown is ERROR`() {
        assertThat(42.codeToAlertMessageCommand()).isEqualTo(AlertMessageResult.ERROR)
    }

    @Test fun `AlertMessageResult commandToCode encodes every member`() {
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

    // endregion

    // region NoticeMessageResult enum --------------------------------------------------------------

    @Test fun `NoticeMessageResult codeToNoticeMessageCommand maps every known code`() {
        assertThat(1.codeToNoticeMessageCommand()).isEqualTo(NoticeMessageResult.REMAIN_EXCEED)
        assertThat(2.codeToNoticeMessageCommand()).isEqualTo(NoticeMessageResult.EXPIRED_NOTICE)
        assertThat(3.codeToNoticeMessageCommand()).isEqualTo(NoticeMessageResult.INSPECTING)
        assertThat(26.codeToNoticeMessageCommand()).isEqualTo(NoticeMessageResult.SYNC_TIME)
        assertThat(27.codeToNoticeMessageCommand()).isEqualTo(NoticeMessageResult.GLUCOSE)
    }

    @Test fun `NoticeMessageResult codeToNoticeMessageCommand unknown is ERROR`() {
        assertThat(4.codeToNoticeMessageCommand()).isEqualTo(NoticeMessageResult.ERROR)
    }

    @Test fun `NoticeMessageResult commandToCode encodes every member`() {
        assertThat(NoticeMessageResult.REMAIN_EXCEED.noticeToCode()).isEqualTo(1)
        assertThat(NoticeMessageResult.EXPIRED_NOTICE.noticeToCode()).isEqualTo(2)
        assertThat(NoticeMessageResult.INSPECTING.noticeToCode()).isEqualTo(3)
        assertThat(NoticeMessageResult.SYNC_TIME.noticeToCode()).isEqualTo(26)
        assertThat(NoticeMessageResult.GLUCOSE.noticeToCode()).isEqualTo(27)
        assertThat(NoticeMessageResult.ERROR.noticeToCode()).isEqualTo(-1)
    }

    // endregion

    // region AlarmCause.fromTypeAndCode (used by the report result models) -------------------------

    @Test fun `fromTypeAndCode resolves per-tier codes`() {
        assertThat(AlarmCause.fromTypeAndCode(AlarmType.WARNING, 0x01))
            .isEqualTo(AlarmCause.ALARM_WARNING_LOW_INSULIN)
        assertThat(AlarmCause.fromTypeAndCode(AlarmType.ALERT, 0x01))
            .isEqualTo(AlarmCause.ALARM_ALERT_OUT_OF_INSULIN)
        assertThat(AlarmCause.fromTypeAndCode(AlarmType.NOTICE, 0x01))
            .isEqualTo(AlarmCause.ALARM_NOTICE_LOW_INSULIN)
    }

    @Test fun `fromTypeAndCode distinguishes shared code by value`() {
        assertThat(AlarmCause.fromTypeAndCode(AlarmType.NOTICE, 100, 1))
            .isEqualTo(AlarmCause.ALARM_NOTICE_LGS_FINISHED_DISCONNECTED_PATCH_OR_CGM)
        assertThat(AlarmCause.fromTypeAndCode(AlarmType.NOTICE, 100, 5))
            .isEqualTo(AlarmCause.ALARM_NOTICE_LGS_FINISHED_HIGH_BG)
        // No value -> the value-less LGS-finished catch-all entry.
        assertThat(AlarmCause.fromTypeAndCode(AlarmType.NOTICE, 100))
            .isEqualTo(AlarmCause.ALARM_NOTICE_LGS_FINISHED_UNKNOWN)
    }

    @Test fun `fromTypeAndCode returns ALARM_UNKNOWN for unmatched input`() {
        assertThat(AlarmCause.fromTypeAndCode(AlarmType.WARNING, 0x7F))
            .isEqualTo(AlarmCause.ALARM_UNKNOWN)
        assertThat(AlarmCause.fromTypeAndCode(AlarmType.UNKNOWN_TYPE, null))
            .isEqualTo(AlarmCause.ALARM_UNKNOWN)
    }

    // endregion

    // region createPatchResultModel dispatch -------------------------------------------------------

    @Test fun `patch dispatch - SetTime`() {
        val m = createPatchResultModel(SetTimeResponse(TS, PATCH, result = 0))
        assertThat(m).isInstanceOf(SetTimeResultModel::class.java)
        assertThat((m as SetTimeResultModel).result).isEqualTo(Result.SUCCESS)
    }

    @Test fun `patch dispatch - PatchInformationInquiry`() {
        val m = createPatchResultModel(PatchInformationInquiryResponse(TS, PATCH, result = 1, serialNum = "SN-9"))
        assertThat(m).isInstanceOf(PatchInformationInquiryModel::class.java)
        m as PatchInformationInquiryModel
        assertThat(m.result).isEqualTo(Result.FAILED)
        assertThat(m.serialNum).isEqualTo("SN-9")
    }

    @Test fun `patch dispatch - PatchInformationInquiryDetail`() {
        val m = createPatchResultModel(
            PatchInformationInquiryDetailResponse(TS, PATCH, result = 0, firmVersion = "1.2.3", bootDateTime = "2026", modelName = "CL")
        )
        assertThat(m).isInstanceOf(PatchInformationInquiryDetailModel::class.java)
        m as PatchInformationInquiryDetailModel
        assertThat(m.result).isEqualTo(Result.SUCCESS)
        assertThat(m.firmwareVer).isEqualTo("1.2.3")
        assertThat(m.bootDateTime).isEqualTo("2026")
        assertThat(m.modelName).isEqualTo("CL")
    }

    @Test fun `patch dispatch - SafetyCheck`() {
        val m = createPatchResultModel(SafetyCheckResponse(TS, PATCH, result = 1, volume = 300, durationSeconds = 42))
        assertThat(m).isInstanceOf(SafetyCheckResultModel::class.java)
        m as SafetyCheckResultModel
        assertThat(m.result).isEqualTo(SafetyCheckResult.INSULIN_DEFICIENCY)
        assertThat(m.volume).isEqualTo(300)
        assertThat(m.durationSeconds).isEqualTo(42)
    }

    @Test fun `patch dispatch - ThresholdSet`() {
        val m = createPatchResultModel(ThresholdSetResponse(TS, PATCH, result = 0))
        assertThat(m).isInstanceOf(ThresholdSetResultModel::class.java)
        assertThat((m as ThresholdSetResultModel).result).isEqualTo(Result.SUCCESS)
    }

    @Test fun `patch dispatch - CannulaInsertion`() {
        val m = createPatchResultModel(CannulaInsertionResponse(TS, PATCH, result = 0))
        assertThat(m).isInstanceOf(CannulaInsertionResultModel::class.java)
        assertThat((m as CannulaInsertionResultModel).result).isEqualTo(Result.SUCCESS)
    }

    @Test fun `patch dispatch - CannulaInsertionAck`() {
        val m = createPatchResultModel(CannulaInsertionAckResponse(TS, PATCH, result = 1))
        assertThat(m).isInstanceOf(CannulaInsertionAckResultModel::class.java)
        assertThat((m as CannulaInsertionAckResultModel).result).isEqualTo(Result.FAILED)
    }

    @Test fun `patch dispatch - SetInfusionThreshold`() {
        val m = createPatchResultModel(SetInfusionThresholdResponse(TS, PATCH, type = 7, result = 0))
        assertThat(m).isInstanceOf(SetInfusionThresholdResultModel::class.java)
        m as SetInfusionThresholdResultModel
        assertThat(m.result).isEqualTo(Result.SUCCESS)
        assertThat(m.type).isEqualTo(7)
    }

    @Test fun `patch dispatch - SetBuzzMode`() {
        val m = createPatchResultModel(SetBuzzModeResponse(TS, PATCH, result = 0))
        assertThat(m).isInstanceOf(SetBuzzModeResultModel::class.java)
        assertThat((m as SetBuzzModeResultModel).result).isEqualTo(Result.SUCCESS)
    }

    @Test fun `patch dispatch - ClearReport to SetAlarmClear`() {
        val m = createPatchResultModel(ClearReportResponse(TS, PATCH, result = 0, subId = 2, cause = 5))
        assertThat(m).isInstanceOf(SetAlarmClearResultModel::class.java)
        m as SetAlarmClearResultModel
        assertThat(m.result).isEqualTo(Result.SUCCESS)
        assertThat(m.subId).isEqualTo(2)
        assertThat(m.cause).isEqualTo(5)
    }

    @Test fun `patch dispatch - SetExpiryExtend`() {
        val m = createPatchResultModel(SetExpiryExtendResponse(TS, PATCH, result = 0))
        assertThat(m).isInstanceOf(ExtendPatchExpiryResultModel::class.java)
        assertThat((m as ExtendPatchExpiryResultModel).result).isEqualTo(Result.SUCCESS)
    }

    @Test fun `patch dispatch - StopPump`() {
        val m = createPatchResultModel(StopPumpResponse(TS, PATCH, result = 0))
        assertThat(m).isInstanceOf(StopPumpResultModel::class.java)
        assertThat((m as StopPumpResultModel).result).isEqualTo(Result.SUCCESS)
    }

    @Test fun `patch dispatch - ResumePump maps stop-pump result and mode`() {
        val m = createPatchResultModel(ResumePumpResponse(TS, PATCH, result = 29, mode = 1, causeId = 4))
        assertThat(m).isInstanceOf(ResumePumpResultModel::class.java)
        m as ResumePumpResultModel
        assertThat(m.result).isEqualTo(StopPumpResult.BY_LGS)
        assertThat(m.mode).isEqualTo(InfusionModeResult.BASAL)
        assertThat(m.subId).isEqualTo(4)
    }

    @Test fun `patch dispatch - StopPumpReport maps renamed amount fields`() {
        val m = createPatchResultModel(
            StopPumpReportResponse(
                TS, PATCH, result = 0, mode = 3, causeId = 12,
                infusedBolusAmount = 1.5, unInfusedExtendBolusAmount = 0.4, temperature = 25
            )
        )
        assertThat(m).isInstanceOf(StopPumpReportResultModel::class.java)
        m as StopPumpReportResultModel
        assertThat(m.result).isEqualTo(StopPumpResult.BY_REQ)
        assertThat(m.mode).isEqualTo(InfusionModeResult.IMME_BOLUS)
        assertThat(m.subId).isEqualTo(12)
        assertThat(m.infusedBolusInfusionAmount).isEqualTo(1.5)
        assertThat(m.infusedBasalInfusionAmount).isEqualTo(0.4)
        assertThat(m.temperature).isEqualTo(25)
    }

    @Test fun `patch dispatch - RetrieveInfusionStatus maps enums and passthroughs`() {
        val m = createPatchResultModel(
            RetrieveInfusionStatusResponse(
                TS, PATCH, subId = 2, runningMinutes = 60, remains = 120.0,
                infusedTotalBasalAmount = 10.0, infusedTotalBolusAmount = 5.0,
                pumpState = 2, mode = 5, infusedSetMinutes = 30,
                currentInfusedProgramVolume = 2.5, realInfusedTime = 15
            )
        )
        assertThat(m).isInstanceOf(InfusionInfoReportResultModel::class.java)
        m as InfusionInfoReportResultModel
        assertThat(m.subId).isEqualTo(InfusionInfoResult.BY_30MIN_RPT)
        assertThat(m.pumpState).isEqualTo(PumpStateResult.RUNNING)
        assertThat(m.mode).isEqualTo(InfusionModeResult.EXTEND_BOLUS)
        assertThat(m.runningMinutes).isEqualTo(60)
        assertThat(m.remains).isEqualTo(120.0)
        assertThat(m.infusedTotalBasalAmount).isEqualTo(10.0)
        assertThat(m.infusedTotalBolusAmount).isEqualTo(5.0)
        assertThat(m.infuseSetMinutes).isEqualTo(30)
        assertThat(m.currentInfusedProgramVolume).isEqualTo(2.5)
        assertThat(m.realInfusedTime).isEqualTo(15)
    }

    @Test fun `patch dispatch - SetApplicationStatus`() {
        val m = createPatchResultModel(SetApplicationStatusResponse(TS, PATCH, status = 3))
        assertThat(m).isInstanceOf(SetApplicationStatusResultModel::class.java)
        assertThat((m as SetApplicationStatusResultModel).status).isEqualTo(3)
    }

    @Test fun `patch dispatch - RetrieveAddress`() {
        val m = createPatchResultModel(RetrieveAddressResponse(TS, PATCH, address = "AA:BB", checkSum = "CS"))
        assertThat(m).isInstanceOf(RetrieveAddressResultModel::class.java)
        m as RetrieveAddressResultModel
        assertThat(m.address).isEqualTo("AA:BB")
        assertThat(m.checkSum).isEqualTo("CS")
    }

    @Test fun `patch dispatch - SetDiscard`() {
        val m = createPatchResultModel(SetDiscardResponse(TS, PATCH, result = 0))
        assertThat(m).isInstanceOf(DiscardPatchResultModel::class.java)
        assertThat((m as DiscardPatchResultModel).result).isEqualTo(Result.SUCCESS)
    }

    @Test fun `patch dispatch - RecoveryPatch`() {
        val m = createPatchResultModel(RecoveryPatchResponse(TS, PATCH))
        assertThat(m).isInstanceOf(RecoveryPatchReportResultModel::class.java)
    }

    @Test fun `patch dispatch - WarningReport maps cause via AlarmCause`() {
        val m = createPatchResultModel(WarningReportResponse(TS, PATCH, cause = 0x01, value = 50))
        assertThat(m).isInstanceOf(WarningReportResultModel::class.java)
        m as WarningReportResultModel
        assertThat(m.cause).isEqualTo(AlarmCause.ALARM_WARNING_LOW_INSULIN)
        assertThat(m.value).isEqualTo(50)
    }

    @Test fun `patch dispatch - AlertReport maps cause via AlarmCause`() {
        val m = createPatchResultModel(AlertReportResponse(TS, PATCH, cause = 0x02, value = 7))
        assertThat(m).isInstanceOf(AlertReportResultModel::class.java)
        m as AlertReportResultModel
        assertThat(m.cause).isEqualTo(AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_2)
        assertThat(m.value).isEqualTo(7)
    }

    @Test fun `patch dispatch - NoticeReport maps cause via AlarmCause`() {
        val m = createPatchResultModel(NoticeReportResponse(TS, PATCH, cause = 0x02, value = 3))
        assertThat(m).isInstanceOf(NoticeReportResultModel::class.java)
        m as NoticeReportResultModel
        assertThat(m.cause).isEqualTo(AlarmCause.ALARM_NOTICE_PATCH_EXPIRED)
        assertThat(m.value).isEqualTo(3)
    }

    @Test fun `patch dispatch - AppAuthRpt`() {
        val m = createPatchResultModel(AppAuthRptResponse(TS, PATCH, value = 88))
        assertThat(m).isInstanceOf(AppAuthAckReportResultModel::class.java)
        assertThat((m as AppAuthAckReportResultModel).value).isEqualTo(88)
    }

    @Test fun `patch dispatch - AdditionalPriming`() {
        val m = createPatchResultModel(AdditionalPrimingResponse(TS, PATCH, result = 1))
        assertThat(m).isInstanceOf(AdditionalPrimingResultModel::class.java)
        assertThat((m as AdditionalPrimingResultModel).result).isEqualTo(Result.FAILED)
    }

    @Test fun `patch dispatch - SetThresholdNotice`() {
        val m = createPatchResultModel(SetThresholdNoticeResponse(TS, PATCH, result = 0, type = 4))
        assertThat(m).isInstanceOf(SetThresholdNoticeResultModel::class.java)
        m as SetThresholdNoticeResultModel
        assertThat(m.type).isEqualTo(4)
        assertThat(m.result).isEqualTo(Result.SUCCESS)
    }

    @Test fun `patch dispatch - SetAlertAlarmModel`() {
        val m = createPatchResultModel(SetAlertAlarmModelResponse(TS, PATCH, result = 0))
        assertThat(m).isInstanceOf(AlertAlarmSetResultModel::class.java)
        assertThat((m as AlertAlarmSetResultModel).result).isEqualTo(Result.SUCCESS)
    }

    @Test fun `patch dispatch - AppAuthAckRpt`() {
        val m = createPatchResultModel(AppAuthAckRptResponse(TS, PATCH, result = 0))
        assertThat(m).isInstanceOf(AppAuthAckResultModel::class.java)
        assertThat((m as AppAuthAckResultModel).result).isEqualTo(Result.SUCCESS)
    }

    @Test fun `patch dispatch - AppAlarmOff`() {
        val m = createPatchResultModel(AppAlarmOffResponse(TS, PATCH, result = 1))
        assertThat(m).isInstanceOf(AppAlarmClearResultModel::class.java)
        assertThat((m as AppAlarmClearResultModel).result).isEqualTo(Result.FAILED)
    }

    @Test fun `patch dispatch - RetrieveOperationInfo passthrough`() {
        val m = createPatchResultModel(
            RetrieveOperationInfoResponse(TS, PATCH, mode = 2, pulseCnt = 10, totalNo = 100, count = 5, useMinutes = 33, remains = 77.5)
        )
        assertThat(m).isInstanceOf(RetrieveOperationInfoResultModel::class.java)
        m as RetrieveOperationInfoResultModel
        assertThat(m.mode).isEqualTo(2)
        assertThat(m.pulseCnt).isEqualTo(10)
        assertThat(m.totalNo).isEqualTo(100)
        assertThat(m.count).isEqualTo(5)
        assertThat(m.useMinutes).isEqualTo(33)
        assertThat(m.remains).isEqualTo(77.5)
    }

    @Test fun `patch dispatch - CheckBuzz to AppBuzz`() {
        val m = createPatchResultModel(CheckBuzzResponse(TS, PATCH, result = 0))
        assertThat(m).isInstanceOf(AppBuzzResultModel::class.java)
        assertThat((m as AppBuzzResultModel).result).isEqualTo(Result.SUCCESS)
    }

    @Test fun `patch dispatch returns null when command is not a patch protocol`() {
        assertThat(createPatchResultModel(SetTimeResponse(TS, BASAL, result = 0))).isNull()
    }

    @Test fun `patch dispatch returns null for an unhandled response type`() {
        // A bolus response carried under a patch opcode matches no patch branch -> null.
        assertThat(createPatchResultModel(StartExtendBolusResponse(TS, PATCH, result = 0, expectedTime = 5))).isNull()
    }

    // endregion

    // region createBasalResultModel dispatch -------------------------------------------------------

    @Test fun `basal dispatch - SetBasalProgram`() {
        val m = createBasalResultModel(SetBasalProgramResponse(TS, BASAL, result = 20))
        assertThat(m).isInstanceOf(SetBasalProgramResultModel::class.java)
        assertThat((m as SetBasalProgramResultModel).result).isEqualTo(SetBasalProgramResult.EXCEED_LIMIT)
    }

    @Test fun `basal dispatch - SetBasalProgramAdditional`() {
        val m = createBasalResultModel(SetBasalProgramAdditionalResponse(TS, BASAL, result = 0))
        assertThat(m).isInstanceOf(SetBasalProgramAdditionalResultModel::class.java)
        assertThat((m as SetBasalProgramAdditionalResultModel).result).isEqualTo(SetBasalProgramResult.SUCCESS)
    }

    @Test fun `basal dispatch - UpdateBasalProgram`() {
        val m = createBasalResultModel(UpdateBasalProgramResponse(TS, BASAL, result = 1))
        assertThat(m).isInstanceOf(UpdateBasalProgramResultModel::class.java)
        assertThat((m as UpdateBasalProgramResultModel).result).isEqualTo(SetBasalProgramResult.INSULIN_DEFICIENCY)
    }

    @Test fun `basal dispatch - UpdateBasalProgramAdditional`() {
        val m = createBasalResultModel(UpdateBasalProgramAdditionalResponse(TS, BASAL, result = 12))
        assertThat(m).isInstanceOf(UpdateBasalProgramAdditionalResultModel::class.java)
        assertThat((m as UpdateBasalProgramAdditionalResultModel).result).isEqualTo(SetBasalProgramResult.PUMP_ERROR)
    }

    @Test fun `basal dispatch - StartTempBasalProgram`() {
        val m = createBasalResultModel(StartTempBasalProgramResponse(TS, BASAL, result = 19))
        assertThat(m).isInstanceOf(StartTempBasalProgramResultModel::class.java)
        assertThat((m as StartTempBasalProgramResultModel).result).isEqualTo(SetBasalProgramResult.ABNORMAL_PROGRAM)
    }

    @Test fun `basal dispatch - CancelTempBasalProgram uses plain Result`() {
        val m = createBasalResultModel(CancelTempBasalProgramResponse(TS, BASAL, result = 0))
        assertThat(m).isInstanceOf(CancelTempBasalProgramResultModel::class.java)
        assertThat((m as CancelTempBasalProgramResultModel).result).isEqualTo(Result.SUCCESS)
    }

    @Test fun `basal dispatch - StartBasalProgram`() {
        val m = createBasalResultModel(StartBasalProgramResponse(TS, BASAL))
        assertThat(m).isInstanceOf(StartBasalProgramResultModel::class.java)
    }

    @Test fun `basal dispatch - ResumeBasalProgram passthrough`() {
        val m = createBasalResultModel(
            ResumeBasalProgramResponse(TS, BASAL, segmentNo = 3, infusionSpeed = 1.25, infusionPeriod = 30, insulinRemains = 88.0)
        )
        assertThat(m).isInstanceOf(BasalInfusionResumeResultModel::class.java)
        m as BasalInfusionResumeResultModel
        assertThat(m.segmentNo).isEqualTo(3)
        assertThat(m.infusionSpeed).isEqualTo(1.25)
        assertThat(m.infusionPeriod).isEqualTo(30)
        assertThat(m.insulinRemains).isEqualTo(88.0)
    }

    @Test fun `basal dispatch returns null when command is not a basal protocol`() {
        assertThat(createBasalResultModel(SetBasalProgramResponse(TS, PATCH, result = 0))).isNull()
    }

    // endregion

    // region createBolusResultModel dispatch -------------------------------------------------------

    @Test fun `bolus dispatch - StartImmeBolus`() {
        val m = createBolusResultModel(StartImmeBolusResponse(TS, BOLUS, result = 0, actionId = 9, expectedTime = 120, remain = 44.0))
        assertThat(m).isInstanceOf(StartImmeBolusResultModel::class.java)
        m as StartImmeBolusResultModel
        assertThat(m.result).isEqualTo(SetBolusProgramResult.SUCCESS)
        assertThat(m.actionId).isEqualTo(9)
        assertThat(m.expectedTime).isEqualTo(120)
        assertThat(m.remains).isEqualTo(44.0)
    }

    @Test fun `bolus dispatch - CancelImmeBolus uses plain Result`() {
        val m = createBolusResultModel(CancelImmeBolusResponse(TS, BOLUS, result = 1, remains = 10.0, infusedAmount = 2.0))
        assertThat(m).isInstanceOf(CancelImmeBolusResultModel::class.java)
        m as CancelImmeBolusResultModel
        assertThat(m.result).isEqualTo(Result.FAILED)
        assertThat(m.remains).isEqualTo(10.0)
        assertThat(m.infusedAmount).isEqualTo(2.0)
    }

    @Test fun `bolus dispatch - StartExtendBolus`() {
        val m = createBolusResultModel(StartExtendBolusResponse(TS, BOLUS, result = 20, expectedTime = 300))
        assertThat(m).isInstanceOf(StartExtendBolusResultModel::class.java)
        m as StartExtendBolusResultModel
        assertThat(m.result).isEqualTo(SetBolusProgramResult.EXCEED_LIMIT)
        assertThat(m.expectedTime).isEqualTo(300)
    }

    @Test fun `bolus dispatch - CancelExtendBolus uses plain Result`() {
        val m = createBolusResultModel(CancelExtendBolusResponse(TS, BOLUS, result = 0, infusedAmount = 3.5))
        assertThat(m).isInstanceOf(CancelExtendBolusResultModel::class.java)
        m as CancelExtendBolusResultModel
        assertThat(m.result).isEqualTo(Result.SUCCESS)
        assertThat(m.infusedAmount).isEqualTo(3.5)
    }

    @Test fun `bolus dispatch - DelayExtendBolus passthrough`() {
        val m = createBolusResultModel(DelayExtendBolusResponse(TS, BOLUS, delayedAmount = 1.75, expectedTime = 90))
        assertThat(m).isInstanceOf(DelayExtendBolusReportResultModel::class.java)
        m as DelayExtendBolusReportResultModel
        assertThat(m.delayedAmount).isEqualTo(1.75)
        assertThat(m.expectedTime).isEqualTo(90)
    }

    @Test fun `bolus dispatch returns null when command is not a bolus protocol`() {
        assertThat(createBolusResultModel(StartImmeBolusResponse(TS, PATCH, result = 0, actionId = 0, expectedTime = 0, remain = 0.0))).isNull()
    }

    // endregion

    // region protocol classifiers ------------------------------------------------------------------

    @Test fun `isPatchProtocol true for patch opcodes`() {
        listOf(
            0x11, 0x71, 0x12, 0x72, 0x15, 0x75, 0x16, 0x76, 0x17, 0x77, 0x18, 0x78, 0x19, 0x79,
            0x1A, 0x7A, 0x1B, 0x7B, 0x1C, 0x7C, 0x26, 0x86, 0x27, 0x87, 0x2A, 0x31, 0x91, 0x33,
            0x93, 0x94, 0x35, 0x95, 0x36, 0x96, 0x37, 0x97, 0x38, 0x98, 0x39, 0x99, 0x3A, 0x9A,
            0x3D, 0x9D, 0x9E, 0x3B, 0x9B, 0x3F, 0x9F, 0x4D, 0xA1, 0xA2, 0xA3, 0x47, 0xA7, 0x4A,
            0xBA, 0x4B, 0x1D, 0x7D, 0x48, 0xA8, 0xBB
        ).forEach { assertThat(isPatchProtocol(it)).isTrue() }
    }

    @Test fun `isPatchProtocol false for non-patch opcodes and unknown`() {
        listOf(
            0x13, 0x73, 0x14, 0x74, 0x21, 0x81, 0x22, 0x82, 0x23, 0x83, 0x24, 0x84, 0x25, 0x85,
            0x88, 0x29, 0x89, 0x2B, 0x8B, 0x2C, 0x8C, 0x2D, 0x8D, 0x9C, 0xFF
        ).forEach { assertThat(isPatchProtocol(it)).isFalse() }
    }

    @Test fun `isBasalProtocol true for basal opcodes`() {
        listOf(0x13, 0x73, 0x14, 0x74, 0x21, 0x81, 0x22, 0x82, 0x23, 0x83, 0x88, 0x2B, 0x8B, 0x2D, 0x8D)
            .forEach { assertThat(isBasalProtocol(it)).isTrue() }
    }

    @Test fun `isBasalProtocol false for non-basal opcodes and unknown`() {
        listOf(0x11, 0x12, 0x24, 0x84, 0x25, 0x29, 0x2C, 0xFF)
            .forEach { assertThat(isBasalProtocol(it)).isFalse() }
    }

    @Test fun `isBolusProtocol true for bolus opcodes`() {
        listOf(0x24, 0x84, 0x25, 0x85, 0x29, 0x89, 0x2C, 0x8C)
            .forEach { assertThat(isBolusProtocol(it)).isTrue() }
    }

    @Test fun `isBolusProtocol false for non-bolus opcodes and unknown`() {
        listOf(0x11, 0x13, 0x21, 0x22, 0x88, 0x2B, 0x2D, 0xFF)
            .forEach { assertThat(isBolusProtocol(it)).isFalse() }
    }

    @Test fun `protocol families are mutually exclusive for the representative opcodes`() {
        // Patch opcode -> only patch.
        assertThat(isPatchProtocol(PATCH)).isTrue()
        assertThat(isBasalProtocol(PATCH)).isFalse()
        assertThat(isBolusProtocol(PATCH)).isFalse()
        // Basal opcode -> only basal.
        assertThat(isPatchProtocol(BASAL)).isFalse()
        assertThat(isBasalProtocol(BASAL)).isTrue()
        assertThat(isBolusProtocol(BASAL)).isFalse()
        // Bolus opcode -> only bolus.
        assertThat(isPatchProtocol(BOLUS)).isFalse()
        assertThat(isBasalProtocol(BOLUS)).isFalse()
        assertThat(isBolusProtocol(BOLUS)).isTrue()
    }

    // endregion

    // region response data-class basics ------------------------------------------------------------

    @Test fun `response data classes expose interface fields and copy semantics`() {
        val r = StopPumpReportResponse(
            timestamp = TS, command = PATCH, result = 0, mode = 1, causeId = 2,
            infusedBolusAmount = 1.0, unInfusedExtendBolusAmount = 0.5, temperature = 20
        )
        assertThat(r.timestamp).isEqualTo(TS)
        assertThat(r.command).isEqualTo(PATCH)
        assertThat(r.copy(temperature = 30).temperature).isEqualTo(30)
        assertThat(r.copy(temperature = 30)).isNotEqualTo(r)
        assertThat(r.copy()).isEqualTo(r)
    }

    // endregion
}
