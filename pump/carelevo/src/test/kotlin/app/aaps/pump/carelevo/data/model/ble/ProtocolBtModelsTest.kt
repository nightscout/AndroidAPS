package app.aaps.pump.carelevo.data.model.ble

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure-logic tests for the Carelevo BLE protocol model/response value classes.
 *
 * These are all data classes / a sealed class with no parse or derive logic, so coverage
 * is driven by construction, property access, the [ProtocolRspModel] interface contract,
 * and the compiler-generated equals/hashCode/copy/toString/componentN members.
 */
internal class ProtocolBtModelsTest {

    // ---------------------------------------------------------------------------------------------
    // ProtocolSegmentModel (request model)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `ProtocolSegmentModel exposes its fields`() {
        val model = ProtocolSegmentModel(injectHour = 3, injectMin = 45, injectSpeed = 1.25)
        assertThat(model.injectHour).isEqualTo(3)
        assertThat(model.injectMin).isEqualTo(45)
        assertThat(model.injectSpeed).isEqualTo(1.25)
    }

    @Test
    fun `ProtocolSegmentModel is a ProtocolRequestModel`() {
        val model: ProtocolRequestModel = ProtocolSegmentModel(0, 0, 0.0)
        assertThat(model).isInstanceOf(ProtocolSegmentModel::class.java)
    }

    @Test
    fun `ProtocolSegmentModel equals hashCode copy componentN`() {
        val a = ProtocolSegmentModel(1, 2, 3.0)
        val b = ProtocolSegmentModel(1, 2, 3.0)
        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
        assertThat(a.copy(injectMin = 9)).isEqualTo(ProtocolSegmentModel(1, 9, 3.0))
        assertThat(a).isNotEqualTo(ProtocolSegmentModel(9, 2, 3.0))
        assertThat(a.component1()).isEqualTo(1)
        assertThat(a.component2()).isEqualTo(2)
        assertThat(a.component3()).isEqualTo(3.0)
        assertThat(a.toString()).contains("injectSpeed=3.0")
    }

    // ---------------------------------------------------------------------------------------------
    // ProtocolRspModel interface contract — every response carries timestamp + command
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `all rsp models expose timestamp and command via interface`() {
        val ts = 1_700_000_000_000L
        val cmd = 0x42
        val models: List<ProtocolRspModel> = listOf(
            ProtocolSetTimeRspModel(ts, cmd, result = 0),
            ProtocolAppAuthKeyAckRspModel(ts, cmd, value = 1),
            ProtocolAppAuthAckRspModel(ts, cmd, result = 0),
            ProtocolSafetyCheckRspModel(ts, cmd, result = 0, insulinVolume = 200, durationSeconds = 30),
            ProtocolInfusionThresholdRspModel(ts, cmd, type = 1, result = 0),
            ProtocolBuzzUsageChangeRspModel(ts, cmd, result = 0),
            ProtocolCannulaInsertionStatusRspModel(ts, cmd, result = 0),
            ProtocolCannulaInsertionAckRspModel(ts, cmd, result = 0),
            ProtocolPatchThresholdSetRspModel(ts, cmd, result = 0),
            ProtocolPatchAlertAlarmSetRspModel(ts, cmd, result = 0),
            ProtocolNoticeThresholdRspModel(ts, cmd, result = 0, type = 2),
            ProtocolPatchExpiryExtendRspModel(ts, cmd, result = 0),
            ProtocolPumpStopRspModel(ts, cmd, result = 0),
            ProtocolPumpResumeRspModel(ts, cmd, result = 0, mode = 1, subId = 2),
            ProtocolPumpStopRptModel(ts, cmd, result = 0, cause = 1, mode = 2, subId = 3, completedBolusInfusionVolume = 1.5, unInfusedExtendBolusVolume = 0.5, temperature = 25),
            ProtocolInfusionStatusInquiryRptModel(ts, cmd, subId = 1, patchRunningTime = 100, insulinRemains = 150.0, infusedTotalBasalAmount = 10.0, infusedTotalBolusAmount = 5.0, pumpState = 1, mode = 2, infusedSetMin = 60, currentInfusedProgramVolume = 2.0, realInfusedTime = 55),
            ProtocolPatchInformationInquiryRptModel(ts, cmd, result = 0, serialNum = "SN123"),
            ProtocolPatchInformationInquiryDetailRptModel(ts, cmd, result = 0, firmVersion = "1.0.0", bootDateTime = "2026-07-16", modelName = "CL-1"),
            ProtocolThresholdRetrieveRspModel(ts, cmd, result = 0, insulinDeficiencyAlarmThreshold = 20, expiryAlarmThreshold = 72),
            ProtocolPatchDiscardRspModel(ts, cmd, result = 0),
            ProtocolPatchBuzzInspectionRspModel(ts, cmd, result = 0),
            ProtocolPatchOperationDataRspModel(ts, cmd, mode = 1, pulseCnt = 10, totalNo = 5, count = 2, useMin = 30, remains = 99.5),
            ProtocolAppStatusRspModel(ts, cmd, status = 1),
            ProtocolGlucoseMeasurementAlarmTimerRspModel(ts, cmd, timerId = 3, minutes = 15),
            ProtocolGlucoseTimerForCGMRspModel(ts, cmd, result = 0, triggerType = 1),
            ProtocolGlucoseTimerRptModel(ts, cmd),
            ProtocolPatchAddressRspModel(ts, cmd, macAddress = "AA:BB:CC:DD:EE:FF", checkSum = "1F"),
            ProtocolWarningMsgRptModel(ts, cmd, cause = 1, value = 10),
            ProtocolAlertMsgRptModel(ts, cmd, cause = 2, value = 20),
            ProtocolNoticeMsgRptModel(ts, cmd, cause = 3, value = 30),
            ProtocolMsgSolutionRspModel(ts, cmd, result = 0, subId = 1, cause = 2),
            ProtocolPatchInitRspModel(ts, cmd, mode = 1),
            ProtocolPatchRecoveryRptModel(ts, cmd),
            ProtocolAdditionalPrimingRspModel(ts, cmd, result = 0),
            ProtocolBasalProgramSetRspModel(ts, cmd, result = 0),
            ProtocolAdditionalBasalProgramSetRspModel(ts, cmd, result = 0),
            ProtocolBasalInfusionChangeRspModel(ts, cmd, result = 0),
            ProtocolAdditionalBasalInfusionChangeRspModel(ts, cmd, result = 0),
            ProtocolBasalInfusionResumeRspModel(ts, cmd, segmentNo = 1, infusionSpeed = 1.0, infusionPeriod = 30, insulinRemains = 100.0),
            ProtocolTempBasalInfusionRspModel(ts, cmd, result = 0),
            ProtocolBasalInfusionStartRspModel(ts, cmd),
            ProtocolTempBasalInfusionCancelRspModel(ts, cmd, result = 0),
            ProtocolImmeBolusInfusionRspModel(ts, cmd, actionId = 7, result = 0, expectedTime = 120, remains = 99.0),
            ProtocolExtendBolusInfusionRspModel(ts, cmd, result = 0, expectedTime = 300),
            ProtocolExtendBolusInfusionCancelRspModel(ts, cmd, result = 0, infusedAmount = 1.5),
            ProtocolBolusInfusionCancelRspModel(ts, cmd, result = 0, insulinRemains = 98.0, infusedAmount = 2.0),
            ProtocolExtendBolusDelayRptModel(ts, cmd, delayedAmount = 0.5, expectedTime = 60)
        )
        assertThat(models).hasSize(47)
        models.forEach {
            assertThat(it.timestamp).isEqualTo(ts)
            assertThat(it.command).isEqualTo(cmd)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Field-level assertions for models carrying extra payload
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `ProtocolSetTimeRspModel fields`() {
        val m = ProtocolSetTimeRspModel(1L, 2, result = 3)
        assertThat(m.result).isEqualTo(3)
        assertThat(m.copy(result = 9).result).isEqualTo(9)
        assertThat(m).isEqualTo(ProtocolSetTimeRspModel(1L, 2, 3))
    }

    @Test
    fun `ProtocolAppAuthKeyAckRspModel fields`() {
        val m = ProtocolAppAuthKeyAckRspModel(1L, 2, value = 5)
        assertThat(m.value).isEqualTo(5)
        assertThat(m).isNotEqualTo(ProtocolAppAuthKeyAckRspModel(1L, 2, value = 6))
    }

    @Test
    fun `ProtocolAppAuthAckRspModel fields`() {
        assertThat(ProtocolAppAuthAckRspModel(1L, 2, result = 7).result).isEqualTo(7)
    }

    @Test
    fun `ProtocolSafetyCheckRspModel fields`() {
        val m = ProtocolSafetyCheckRspModel(1L, 2, result = 0, insulinVolume = 200, durationSeconds = 45)
        assertThat(m.result).isEqualTo(0)
        assertThat(m.insulinVolume).isEqualTo(200)
        assertThat(m.durationSeconds).isEqualTo(45)
    }

    @Test
    fun `ProtocolInfusionThresholdRspModel fields`() {
        val m = ProtocolInfusionThresholdRspModel(1L, 2, type = 4, result = 0)
        assertThat(m.type).isEqualTo(4)
        assertThat(m.result).isEqualTo(0)
    }

    @Test
    fun `ProtocolNoticeThresholdRspModel fields`() {
        val m = ProtocolNoticeThresholdRspModel(1L, 2, result = 1, type = 8)
        assertThat(m.result).isEqualTo(1)
        assertThat(m.type).isEqualTo(8)
    }

    @Test
    fun `ProtocolPumpResumeRspModel fields`() {
        val m = ProtocolPumpResumeRspModel(1L, 2, result = 0, mode = 3, subId = 4)
        assertThat(m.mode).isEqualTo(3)
        assertThat(m.subId).isEqualTo(4)
    }

    @Test
    fun `ProtocolPumpStopRptModel fields`() {
        val m = ProtocolPumpStopRptModel(
            timestamp = 1L, command = 2, result = 0, cause = 5, mode = 1, subId = 2,
            completedBolusInfusionVolume = 3.5, unInfusedExtendBolusVolume = 1.25, temperature = 30
        )
        assertThat(m.cause).isEqualTo(5)
        assertThat(m.mode).isEqualTo(1)
        assertThat(m.subId).isEqualTo(2)
        assertThat(m.completedBolusInfusionVolume).isEqualTo(3.5)
        assertThat(m.unInfusedExtendBolusVolume).isEqualTo(1.25)
        assertThat(m.temperature).isEqualTo(30)
    }

    @Test
    fun `ProtocolInfusionStatusInquiryRptModel fields`() {
        val m = ProtocolInfusionStatusInquiryRptModel(
            timestamp = 1L, command = 2, subId = 3, patchRunningTime = 200, insulinRemains = 150.5,
            infusedTotalBasalAmount = 10.5, infusedTotalBolusAmount = 5.25, pumpState = 2, mode = 1,
            infusedSetMin = 120, currentInfusedProgramVolume = 2.75, realInfusedTime = 118
        )
        assertThat(m.subId).isEqualTo(3)
        assertThat(m.patchRunningTime).isEqualTo(200)
        assertThat(m.insulinRemains).isEqualTo(150.5)
        assertThat(m.infusedTotalBasalAmount).isEqualTo(10.5)
        assertThat(m.infusedTotalBolusAmount).isEqualTo(5.25)
        assertThat(m.pumpState).isEqualTo(2)
        assertThat(m.mode).isEqualTo(1)
        assertThat(m.infusedSetMin).isEqualTo(120)
        assertThat(m.currentInfusedProgramVolume).isEqualTo(2.75)
        assertThat(m.realInfusedTime).isEqualTo(118)
    }

    @Test
    fun `ProtocolPatchInformationInquiryRptModel fields`() {
        val m = ProtocolPatchInformationInquiryRptModel(1L, 2, result = 0, serialNum = "SN-9")
        assertThat(m.serialNum).isEqualTo("SN-9")
        assertThat(m.result).isEqualTo(0)
    }

    @Test
    fun `ProtocolPatchInformationInquiryDetailRptModel fields`() {
        val m = ProtocolPatchInformationInquiryDetailRptModel(
            1L, 2, result = 0, firmVersion = "2.1.0", bootDateTime = "2026-07-16 10:00", modelName = "CL-Pro"
        )
        assertThat(m.firmVersion).isEqualTo("2.1.0")
        assertThat(m.bootDateTime).isEqualTo("2026-07-16 10:00")
        assertThat(m.modelName).isEqualTo("CL-Pro")
    }

    @Test
    fun `ProtocolThresholdRetrieveRspModel fields`() {
        val m = ProtocolThresholdRetrieveRspModel(1L, 2, result = 0, insulinDeficiencyAlarmThreshold = 25, expiryAlarmThreshold = 72)
        assertThat(m.insulinDeficiencyAlarmThreshold).isEqualTo(25)
        assertThat(m.expiryAlarmThreshold).isEqualTo(72)
    }

    @Test
    fun `ProtocolPatchOperationDataRspModel fields`() {
        val m = ProtocolPatchOperationDataRspModel(1L, 2, mode = 1, pulseCnt = 12, totalNo = 5, count = 2, useMin = 30, remains = 88.5)
        assertThat(m.mode).isEqualTo(1)
        assertThat(m.pulseCnt).isEqualTo(12)
        assertThat(m.totalNo).isEqualTo(5)
        assertThat(m.count).isEqualTo(2)
        assertThat(m.useMin).isEqualTo(30)
        assertThat(m.remains).isEqualTo(88.5)
    }

    @Test
    fun `ProtocolAppStatusRspModel fields`() {
        assertThat(ProtocolAppStatusRspModel(1L, 2, status = 9).status).isEqualTo(9)
    }

    @Test
    fun `ProtocolGlucoseMeasurementAlarmTimerRspModel fields`() {
        val m = ProtocolGlucoseMeasurementAlarmTimerRspModel(1L, 2, timerId = 4, minutes = 15)
        assertThat(m.timerId).isEqualTo(4)
        assertThat(m.minutes).isEqualTo(15)
    }

    @Test
    fun `ProtocolGlucoseTimerForCGMRspModel fields`() {
        val m = ProtocolGlucoseTimerForCGMRspModel(1L, 2, result = 0, triggerType = 3)
        assertThat(m.result).isEqualTo(0)
        assertThat(m.triggerType).isEqualTo(3)
    }

    @Test
    fun `ProtocolPatchAddressRspModel fields`() {
        val m = ProtocolPatchAddressRspModel(1L, 2, macAddress = "AA:BB:CC:DD:EE:FF", checkSum = "3C")
        assertThat(m.macAddress).isEqualTo("AA:BB:CC:DD:EE:FF")
        assertThat(m.checkSum).isEqualTo("3C")
    }

    @Test
    fun `ProtocolWarningMsgRptModel fields`() {
        val m = ProtocolWarningMsgRptModel(1L, 2, cause = 11, value = 22)
        assertThat(m.cause).isEqualTo(11)
        assertThat(m.value).isEqualTo(22)
    }

    @Test
    fun `ProtocolAlertMsgRptModel fields`() {
        val m = ProtocolAlertMsgRptModel(1L, 2, cause = 33, value = 44)
        assertThat(m.cause).isEqualTo(33)
        assertThat(m.value).isEqualTo(44)
    }

    @Test
    fun `ProtocolNoticeMsgRptModel fields`() {
        val m = ProtocolNoticeMsgRptModel(1L, 2, cause = 55, value = 66)
        assertThat(m.cause).isEqualTo(55)
        assertThat(m.value).isEqualTo(66)
    }

    @Test
    fun `three message rpt models with equal payload are still distinct types`() {
        val warning = ProtocolWarningMsgRptModel(1L, 2, cause = 1, value = 1)
        val alert = ProtocolAlertMsgRptModel(1L, 2, cause = 1, value = 1)
        val notice = ProtocolNoticeMsgRptModel(1L, 2, cause = 1, value = 1)
        assertThat(warning).isNotEqualTo(alert)
        assertThat(alert).isNotEqualTo(notice)
        assertThat(warning).isNotEqualTo(notice)
    }

    @Test
    fun `ProtocolMsgSolutionRspModel fields`() {
        val m = ProtocolMsgSolutionRspModel(1L, 2, result = 0, subId = 3, cause = 7)
        assertThat(m.result).isEqualTo(0)
        assertThat(m.subId).isEqualTo(3)
        assertThat(m.cause).isEqualTo(7)
    }

    @Test
    fun `ProtocolPatchInitRspModel fields`() {
        assertThat(ProtocolPatchInitRspModel(1L, 2, mode = 4).mode).isEqualTo(4)
    }

    @Test
    fun `ProtocolBasalInfusionResumeRspModel fields`() {
        val m = ProtocolBasalInfusionResumeRspModel(1L, 2, segmentNo = 3, infusionSpeed = 1.5, infusionPeriod = 60, insulinRemains = 120.0)
        assertThat(m.segmentNo).isEqualTo(3)
        assertThat(m.infusionSpeed).isEqualTo(1.5)
        assertThat(m.infusionPeriod).isEqualTo(60)
        assertThat(m.insulinRemains).isEqualTo(120.0)
    }

    @Test
    fun `ProtocolImmeBolusInfusionRspModel fields`() {
        val m = ProtocolImmeBolusInfusionRspModel(1L, 2, actionId = 8, result = 0, expectedTime = 150, remains = 95.5)
        assertThat(m.actionId).isEqualTo(8)
        assertThat(m.result).isEqualTo(0)
        assertThat(m.expectedTime).isEqualTo(150)
        assertThat(m.remains).isEqualTo(95.5)
    }

    @Test
    fun `ProtocolExtendBolusInfusionRspModel fields`() {
        val m = ProtocolExtendBolusInfusionRspModel(1L, 2, result = 0, expectedTime = 900)
        assertThat(m.result).isEqualTo(0)
        assertThat(m.expectedTime).isEqualTo(900)
    }

    @Test
    fun `ProtocolExtendBolusInfusionCancelRspModel fields`() {
        val m = ProtocolExtendBolusInfusionCancelRspModel(1L, 2, result = 0, infusedAmount = 1.75)
        assertThat(m.result).isEqualTo(0)
        assertThat(m.infusedAmount).isEqualTo(1.75)
    }

    @Test
    fun `ProtocolBolusInfusionCancelRspModel fields`() {
        val m = ProtocolBolusInfusionCancelRspModel(1L, 2, result = 0, insulinRemains = 97.5, infusedAmount = 2.5)
        assertThat(m.result).isEqualTo(0)
        assertThat(m.insulinRemains).isEqualTo(97.5)
        assertThat(m.infusedAmount).isEqualTo(2.5)
    }

    @Test
    fun `ProtocolExtendBolusDelayRptModel fields`() {
        val m = ProtocolExtendBolusDelayRptModel(1L, 2, delayedAmount = 0.75, expectedTime = 120)
        assertThat(m.delayedAmount).isEqualTo(0.75)
        assertThat(m.expectedTime).isEqualTo(120)
    }

    @Test
    fun `result-only rsp models carry their single payload`() {
        assertThat(ProtocolBuzzUsageChangeRspModel(1L, 2, result = 1).result).isEqualTo(1)
        assertThat(ProtocolCannulaInsertionStatusRspModel(1L, 2, result = 1).result).isEqualTo(1)
        assertThat(ProtocolCannulaInsertionAckRspModel(1L, 2, result = 1).result).isEqualTo(1)
        assertThat(ProtocolPatchThresholdSetRspModel(1L, 2, result = 1).result).isEqualTo(1)
        assertThat(ProtocolPatchAlertAlarmSetRspModel(1L, 2, result = 1).result).isEqualTo(1)
        assertThat(ProtocolPatchExpiryExtendRspModel(1L, 2, result = 1).result).isEqualTo(1)
        assertThat(ProtocolPumpStopRspModel(1L, 2, result = 1).result).isEqualTo(1)
        assertThat(ProtocolPatchDiscardRspModel(1L, 2, result = 1).result).isEqualTo(1)
        assertThat(ProtocolPatchBuzzInspectionRspModel(1L, 2, result = 1).result).isEqualTo(1)
        assertThat(ProtocolAdditionalPrimingRspModel(1L, 2, result = 1).result).isEqualTo(1)
        assertThat(ProtocolBasalProgramSetRspModel(1L, 2, result = 1).result).isEqualTo(1)
        assertThat(ProtocolAdditionalBasalProgramSetRspModel(1L, 2, result = 1).result).isEqualTo(1)
        assertThat(ProtocolBasalInfusionChangeRspModel(1L, 2, result = 1).result).isEqualTo(1)
        assertThat(ProtocolAdditionalBasalInfusionChangeRspModel(1L, 2, result = 1).result).isEqualTo(1)
        assertThat(ProtocolTempBasalInfusionRspModel(1L, 2, result = 1).result).isEqualTo(1)
        assertThat(ProtocolTempBasalInfusionCancelRspModel(1L, 2, result = 1).result).isEqualTo(1)
    }

    @Test
    fun `parameterless-payload rpt models only carry timestamp and command`() {
        val glucose = ProtocolGlucoseTimerRptModel(1L, 2)
        val start = ProtocolBasalInfusionStartRspModel(3L, 4)
        val recovery = ProtocolPatchRecoveryRptModel(5L, 6)
        assertThat(glucose.timestamp).isEqualTo(1L)
        assertThat(glucose.command).isEqualTo(2)
        assertThat(start.timestamp).isEqualTo(3L)
        assertThat(start.command).isEqualTo(4)
        assertThat(recovery.timestamp).isEqualTo(5L)
        assertThat(recovery.command).isEqualTo(6)
        assertThat(glucose).isEqualTo(ProtocolGlucoseTimerRptModel(1L, 2))
        assertThat(glucose).isNotEqualTo(ProtocolGlucoseTimerRptModel(1L, 3))
    }

    // ---------------------------------------------------------------------------------------------
    // BleResponse sealed hierarchy
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `BleResponse RspResponse wraps typed data`() {
        val payload = ProtocolAppStatusRspModel(1L, 2, status = 1)
        val response: BleResponse<ProtocolAppStatusRspModel> = BleResponse.RspResponse(payload)
        assertThat(response).isInstanceOf(BleResponse.RspResponse::class.java)
        assertThat((response as BleResponse.RspResponse).data).isSameInstanceAs(payload)
    }

    @Test
    fun `BleResponse RspResponse equals hashCode copy`() {
        val a = BleResponse.RspResponse("payload")
        val b = BleResponse.RspResponse("payload")
        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
        assertThat(a.copy(data = "other")).isEqualTo(BleResponse.RspResponse("other"))
        assertThat(a.component1()).isEqualTo("payload")
    }

    @Test
    fun `BleResponse Failure carries a message`() {
        val failure: BleResponse<Nothing> = BleResponse.Failure("timeout")
        assertThat(failure).isInstanceOf(BleResponse.Failure::class.java)
        assertThat((failure as BleResponse.Failure).message).isEqualTo("timeout")
        assertThat(failure).isEqualTo(BleResponse.Failure("timeout"))
        assertThat(failure).isNotEqualTo(BleResponse.Failure("other"))
        assertThat(failure.copy(message = "x").message).isEqualTo("x")
    }

    @Test
    fun `BleResponse Error carries a throwable`() {
        val cause = IllegalStateException("boom")
        val error: BleResponse<Nothing> = BleResponse.Error(cause)
        assertThat(error).isInstanceOf(BleResponse.Error::class.java)
        assertThat((error as BleResponse.Error).e).isSameInstanceAs(cause)
        assertThat(error).isEqualTo(BleResponse.Error(cause))
        assertThat(error.copy(e = cause).e).isSameInstanceAs(cause)
    }

    @Test
    fun `BleResponse subtypes are distinguishable in a when`() {
        fun describe(r: BleResponse<Int>): String = when (r) {
            is BleResponse.RspResponse -> "rsp:${r.data}"
            is BleResponse.Failure     -> "fail:${r.message}"
            is BleResponse.Error       -> "err:${r.e.message}"
        }
        assertThat(describe(BleResponse.RspResponse(7))).isEqualTo("rsp:7")
        assertThat(describe(BleResponse.Failure("nope"))).isEqualTo("fail:nope")
        assertThat(describe(BleResponse.Error(RuntimeException("bad")))).isEqualTo("err:bad")
    }

    @Test
    fun `BleResponse RspResponse and Failure are not equal`() {
        val rsp: BleResponse<String> = BleResponse.RspResponse("x")
        val fail: BleResponse<String> = BleResponse.Failure("x")
        assertThat(rsp).isNotEqualTo(fail)
    }
}
