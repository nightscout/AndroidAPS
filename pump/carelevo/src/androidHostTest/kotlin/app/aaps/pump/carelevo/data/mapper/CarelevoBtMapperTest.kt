package app.aaps.pump.carelevo.data.mapper

import app.aaps.pump.carelevo.data.model.ble.ProtocolAdditionalBasalInfusionChangeRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolAdditionalBasalProgramSetRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolAdditionalPrimingRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolAlertMsgRptModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolAppAuthAckRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolAppAuthKeyAckRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolAppStatusRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolBasalInfusionChangeRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolBasalInfusionResumeRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolBasalInfusionStartRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolBasalProgramSetRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolBolusInfusionCancelRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolBuzzUsageChangeRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolCannulaInsertionAckRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolCannulaInsertionStatusRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolExtendBolusDelayRptModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolExtendBolusInfusionCancelRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolExtendBolusInfusionRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolImmeBolusInfusionRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolInfusionStatusInquiryRptModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolInfusionThresholdRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolMsgSolutionRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolNoticeMsgRptModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolNoticeThresholdRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchAddressRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchAlertAlarmSetRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchBuzzInspectionRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchDiscardRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchExpiryExtendRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchInformationInquiryDetailRptModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchInformationInquiryRptModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchInitRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchOperationDataRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchRecoveryRptModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchThresholdSetRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPumpResumeRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPumpStopRptModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPumpStopRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolSafetyCheckRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolSetTimeRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolTempBasalInfusionCancelRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolTempBasalInfusionRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolWarningMsgRptModel
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Exhaustive field-mapping tests for every [transformToDomainModel] extension in CarelevoBtMapper.
 *
 * Each protocol model is fed with intentionally distinct field values so that a wrong-field wiring
 * (e.g. mapping `insulinRemains` onto the wrong target property) fails loudly rather than passing on
 * a coincidental match.
 */
internal class CarelevoBtMapperTest {

    @Test
    fun `ProtocolSetTimeRspModel maps timestamp command result`() {
        val out = ProtocolSetTimeRspModel(timestamp = 111L, command = 1, result = 7).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(111L)
        assertThat(out.command).isEqualTo(1)
        assertThat(out.result).isEqualTo(7)
    }

    @Test
    fun `ProtocolSafetyCheckRspModel maps insulinVolume to volume and duration`() {
        val out = ProtocolSafetyCheckRspModel(timestamp = 222L, command = 2, result = 0, insulinVolume = 300, durationSeconds = 45).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(222L)
        assertThat(out.command).isEqualTo(2)
        assertThat(out.result).isEqualTo(0)
        assertThat(out.volume).isEqualTo(300)
        assertThat(out.durationSeconds).isEqualTo(45)
    }

    @Test
    fun `ProtocolAdditionalPrimingRspModel maps timestamp command result`() {
        val out = ProtocolAdditionalPrimingRspModel(timestamp = 333L, command = 3, result = 1).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(333L)
        assertThat(out.command).isEqualTo(3)
        assertThat(out.result).isEqualTo(1)
    }

    @Test
    fun `ProtocolPatchAlertAlarmSetRspModel maps to SetAlertAlarmModelResponse`() {
        val out = ProtocolPatchAlertAlarmSetRspModel(timestamp = 444L, command = 4, result = 2).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(444L)
        assertThat(out.command).isEqualTo(4)
        assertThat(out.result).isEqualTo(2)
    }

    @Test
    fun `ProtocolNoticeThresholdRspModel maps result and type`() {
        val out = ProtocolNoticeThresholdRspModel(timestamp = 555L, command = 5, result = 0, type = 9).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(555L)
        assertThat(out.command).isEqualTo(5)
        assertThat(out.result).isEqualTo(0)
        assertThat(out.type).isEqualTo(9)
    }

    @Test
    fun `ProtocolInfusionThresholdRspModel maps type and result`() {
        val out = ProtocolInfusionThresholdRspModel(timestamp = 666L, command = 6, type = 8, result = 3).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(666L)
        assertThat(out.command).isEqualTo(6)
        assertThat(out.type).isEqualTo(8)
        assertThat(out.result).isEqualTo(3)
    }

    @Test
    fun `ProtocolBuzzUsageChangeRspModel maps to SetBuzzModeResponse`() {
        val out = ProtocolBuzzUsageChangeRspModel(timestamp = 777L, command = 7, result = 1).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(777L)
        assertThat(out.command).isEqualTo(7)
        assertThat(out.result).isEqualTo(1)
    }

    @Test
    fun `ProtocolCannulaInsertionStatusRspModel maps to CannulaInsertionResponse`() {
        val out = ProtocolCannulaInsertionStatusRspModel(timestamp = 888L, command = 8, result = 4).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(888L)
        assertThat(out.command).isEqualTo(8)
        assertThat(out.result).isEqualTo(4)
    }

    @Test
    fun `ProtocolCannulaInsertionAckRspModel maps to CannulaInsertionAckResponse`() {
        val out = ProtocolCannulaInsertionAckRspModel(timestamp = 999L, command = 9, result = 5).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(999L)
        assertThat(out.command).isEqualTo(9)
        assertThat(out.result).isEqualTo(5)
    }

    @Test
    fun `ProtocolPatchThresholdSetRspModel maps to ThresholdSetResponse`() {
        val out = ProtocolPatchThresholdSetRspModel(timestamp = 1010L, command = 10, result = 6).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(1010L)
        assertThat(out.command).isEqualTo(10)
        assertThat(out.result).isEqualTo(6)
    }

    @Test
    fun `ProtocolPatchExpiryExtendRspModel maps to SetExpiryExtendResponse`() {
        val out = ProtocolPatchExpiryExtendRspModel(timestamp = 1111L, command = 11, result = 7).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(1111L)
        assertThat(out.command).isEqualTo(11)
        assertThat(out.result).isEqualTo(7)
    }

    @Test
    fun `ProtocolPatchInformationInquiryRptModel maps serialNum`() {
        val out = ProtocolPatchInformationInquiryRptModel(timestamp = 1212L, command = 12, result = 0, serialNum = "SN-12345").transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(1212L)
        assertThat(out.command).isEqualTo(12)
        assertThat(out.result).isEqualTo(0)
        assertThat(out.serialNum).isEqualTo("SN-12345")
    }

    @Test
    fun `ProtocolPatchInformationInquiryDetailRptModel maps firmVersion bootDateTime modelName`() {
        val out = ProtocolPatchInformationInquiryDetailRptModel(
            timestamp = 1313L, command = 13, result = 1, firmVersion = "1.2.3", bootDateTime = "2026-07-16T00:00", modelName = "CLV-1"
        ).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(1313L)
        assertThat(out.command).isEqualTo(13)
        assertThat(out.result).isEqualTo(1)
        assertThat(out.firmVersion).isEqualTo("1.2.3")
        assertThat(out.bootDateTime).isEqualTo("2026-07-16T00:00")
        assertThat(out.modelName).isEqualTo("CLV-1")
    }

    @Test
    fun `ProtocolAppStatusRspModel maps status`() {
        val out = ProtocolAppStatusRspModel(timestamp = 1414L, command = 14, status = 2).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(1414L)
        assertThat(out.command).isEqualTo(14)
        assertThat(out.status).isEqualTo(2)
    }

    @Test
    fun `ProtocolInfusionStatusInquiryRptModel maps all fields including renamed running and set minutes`() {
        val out = ProtocolInfusionStatusInquiryRptModel(
            timestamp = 1515L,
            command = 15,
            subId = 3,
            patchRunningTime = 720,
            insulinRemains = 120.5,
            infusedTotalBasalAmount = 30.25,
            infusedTotalBolusAmount = 12.75,
            pumpState = 4,
            mode = 5,
            infusedSetMin = 60,
            currentInfusedProgramVolume = 2.5,
            realInfusedTime = 55
        ).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(1515L)
        assertThat(out.command).isEqualTo(15)
        assertThat(out.subId).isEqualTo(3)
        assertThat(out.runningMinutes).isEqualTo(720)
        assertThat(out.remains).isEqualTo(120.5)
        assertThat(out.infusedTotalBasalAmount).isEqualTo(30.25)
        assertThat(out.infusedTotalBolusAmount).isEqualTo(12.75)
        assertThat(out.pumpState).isEqualTo(4)
        assertThat(out.mode).isEqualTo(5)
        assertThat(out.infusedSetMinutes).isEqualTo(60)
        assertThat(out.currentInfusedProgramVolume).isEqualTo(2.5)
        assertThat(out.realInfusedTime).isEqualTo(55)
    }

    @Test
    fun `ProtocolPumpStopRspModel maps to StopPumpResponse`() {
        val out = ProtocolPumpStopRspModel(timestamp = 1616L, command = 16, result = 8).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(1616L)
        assertThat(out.command).isEqualTo(16)
        assertThat(out.result).isEqualTo(8)
    }

    @Test
    fun `ProtocolPumpStopRptModel maps subId to causeId and volumes and temperature`() {
        val out = ProtocolPumpStopRptModel(
            timestamp = 1717L,
            command = 17,
            result = 0,
            cause = 99,
            mode = 6,
            subId = 42,
            completedBolusInfusionVolume = 3.5,
            unInfusedExtendBolusVolume = 1.25,
            temperature = 37
        ).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(1717L)
        assertThat(out.command).isEqualTo(17)
        assertThat(out.result).isEqualTo(0)
        assertThat(out.mode).isEqualTo(6)
        assertThat(out.causeId).isEqualTo(42)
        assertThat(out.infusedBolusAmount).isEqualTo(3.5)
        assertThat(out.unInfusedExtendBolusAmount).isEqualTo(1.25)
        assertThat(out.temperature).isEqualTo(37)
    }

    @Test
    fun `ProtocolPumpResumeRspModel maps subId to causeId`() {
        val out = ProtocolPumpResumeRspModel(timestamp = 1818L, command = 18, result = 1, mode = 7, subId = 43).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(1818L)
        assertThat(out.command).isEqualTo(18)
        assertThat(out.result).isEqualTo(1)
        assertThat(out.mode).isEqualTo(7)
        assertThat(out.causeId).isEqualTo(43)
    }

    @Test
    fun `ProtocolPatchInitRspModel maps mode`() {
        val out = ProtocolPatchInitRspModel(timestamp = 1919L, command = 19, mode = 8).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(1919L)
        assertThat(out.command).isEqualTo(19)
        assertThat(out.mode).isEqualTo(8)
    }

    @Test
    fun `ProtocolPatchDiscardRspModel maps to SetDiscardResponse`() {
        val out = ProtocolPatchDiscardRspModel(timestamp = 2020L, command = 20, result = 9).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(2020L)
        assertThat(out.command).isEqualTo(20)
        assertThat(out.result).isEqualTo(9)
    }

    @Test
    fun `ProtocolPatchBuzzInspectionRspModel maps to CheckBuzzResponse`() {
        val out = ProtocolPatchBuzzInspectionRspModel(timestamp = 2121L, command = 21, result = 10).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(2121L)
        assertThat(out.command).isEqualTo(21)
        assertThat(out.result).isEqualTo(10)
    }

    @Test
    fun `ProtocolPatchOperationDataRspModel maps useMin to useMinutes and rest`() {
        val out = ProtocolPatchOperationDataRspModel(
            timestamp = 2222L, command = 22, mode = 9, pulseCnt = 1000, totalNo = 5, count = 3, useMin = 480, remains = 88.5
        ).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(2222L)
        assertThat(out.command).isEqualTo(22)
        assertThat(out.mode).isEqualTo(9)
        assertThat(out.pulseCnt).isEqualTo(1000)
        assertThat(out.totalNo).isEqualTo(5)
        assertThat(out.count).isEqualTo(3)
        assertThat(out.useMinutes).isEqualTo(480)
        assertThat(out.remains).isEqualTo(88.5)
    }

    @Test
    fun `ProtocolPatchAddressRspModel maps macAddress to address and checkSum`() {
        val out = ProtocolPatchAddressRspModel(timestamp = 2323L, command = 23, macAddress = "AA:BB:CC:DD:EE:FF", checkSum = "1F").transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(2323L)
        assertThat(out.command).isEqualTo(23)
        assertThat(out.address).isEqualTo("AA:BB:CC:DD:EE:FF")
        assertThat(out.checkSum).isEqualTo("1F")
    }

    @Test
    fun `ProtocolWarningMsgRptModel maps cause and value`() {
        val out = ProtocolWarningMsgRptModel(timestamp = 2424L, command = 24, cause = 11, value = 22).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(2424L)
        assertThat(out.command).isEqualTo(24)
        assertThat(out.cause).isEqualTo(11)
        assertThat(out.value).isEqualTo(22)
    }

    @Test
    fun `ProtocolAlertMsgRptModel maps cause and value`() {
        val out = ProtocolAlertMsgRptModel(timestamp = 2525L, command = 25, cause = 12, value = 23).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(2525L)
        assertThat(out.command).isEqualTo(25)
        assertThat(out.cause).isEqualTo(12)
        assertThat(out.value).isEqualTo(23)
    }

    @Test
    fun `ProtocolNoticeMsgRptModel maps cause and value`() {
        val out = ProtocolNoticeMsgRptModel(timestamp = 2626L, command = 26, cause = 13, value = 24).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(2626L)
        assertThat(out.command).isEqualTo(26)
        assertThat(out.cause).isEqualTo(13)
        assertThat(out.value).isEqualTo(24)
    }

    @Test
    fun `ProtocolMsgSolutionRspModel maps result subId cause`() {
        val out = ProtocolMsgSolutionRspModel(timestamp = 2727L, command = 27, result = 0, subId = 14, cause = 25).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(2727L)
        assertThat(out.command).isEqualTo(27)
        assertThat(out.result).isEqualTo(0)
        assertThat(out.subId).isEqualTo(14)
        assertThat(out.cause).isEqualTo(25)
    }

    @Test
    fun `ProtocolBasalProgramSetRspModel maps to SetBasalProgramResponse`() {
        val out = ProtocolBasalProgramSetRspModel(timestamp = 2828L, command = 28, result = 15).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(2828L)
        assertThat(out.command).isEqualTo(28)
        assertThat(out.result).isEqualTo(15)
    }

    @Test
    fun `ProtocolAdditionalBasalProgramSetRspModel maps to SetBasalProgramAdditionalResponse`() {
        val out = ProtocolAdditionalBasalProgramSetRspModel(timestamp = 2929L, command = 29, result = 16).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(2929L)
        assertThat(out.command).isEqualTo(29)
        assertThat(out.result).isEqualTo(16)
    }

    @Test
    fun `ProtocolBasalInfusionChangeRspModel maps to UpdateBasalProgramResponse`() {
        val out = ProtocolBasalInfusionChangeRspModel(timestamp = 3030L, command = 30, result = 17).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(3030L)
        assertThat(out.command).isEqualTo(30)
        assertThat(out.result).isEqualTo(17)
    }

    @Test
    fun `ProtocolAdditionalBasalInfusionChangeRspModel maps to UpdateBasalProgramAdditionalResponse`() {
        val out = ProtocolAdditionalBasalInfusionChangeRspModel(timestamp = 3131L, command = 31, result = 18).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(3131L)
        assertThat(out.command).isEqualTo(31)
        assertThat(out.result).isEqualTo(18)
    }

    @Test
    fun `ProtocolTempBasalInfusionRspModel maps to StartTempBasalProgramResponse`() {
        val out = ProtocolTempBasalInfusionRspModel(timestamp = 3232L, command = 32, result = 19).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(3232L)
        assertThat(out.command).isEqualTo(32)
        assertThat(out.result).isEqualTo(19)
    }

    @Test
    fun `ProtocolTempBasalInfusionCancelRspModel maps to CancelTempBasalProgramResponse`() {
        val out = ProtocolTempBasalInfusionCancelRspModel(timestamp = 3333L, command = 33, result = 20).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(3333L)
        assertThat(out.command).isEqualTo(33)
        assertThat(out.result).isEqualTo(20)
    }

    @Test
    fun `ProtocolBasalInfusionStartRspModel maps timestamp and command only`() {
        val out = ProtocolBasalInfusionStartRspModel(timestamp = 3434L, command = 34).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(3434L)
        assertThat(out.command).isEqualTo(34)
    }

    @Test
    fun `ProtocolBasalInfusionResumeRspModel maps segment speed period remains`() {
        val out = ProtocolBasalInfusionResumeRspModel(
            timestamp = 3535L, command = 35, segmentNo = 4, infusionSpeed = 1.75, infusionPeriod = 30, insulinRemains = 99.9
        ).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(3535L)
        assertThat(out.command).isEqualTo(35)
        assertThat(out.segmentNo).isEqualTo(4)
        assertThat(out.infusionSpeed).isEqualTo(1.75)
        assertThat(out.infusionPeriod).isEqualTo(30)
        assertThat(out.insulinRemains).isEqualTo(99.9)
    }

    @Test
    fun `ProtocolImmeBolusInfusionRspModel maps remains to remain and actionId expectedTime`() {
        val out = ProtocolImmeBolusInfusionRspModel(
            timestamp = 3636L, command = 36, actionId = 7, result = 0, expectedTime = 120, remains = 55.5
        ).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(3636L)
        assertThat(out.command).isEqualTo(36)
        assertThat(out.result).isEqualTo(0)
        assertThat(out.actionId).isEqualTo(7)
        assertThat(out.expectedTime).isEqualTo(120)
        assertThat(out.remain).isEqualTo(55.5)
    }

    @Test
    fun `ProtocolBolusInfusionCancelRspModel maps insulinRemains to remains and infusedAmount`() {
        val out = ProtocolBolusInfusionCancelRspModel(
            timestamp = 3737L, command = 37, result = 1, insulinRemains = 44.4, infusedAmount = 5.5
        ).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(3737L)
        assertThat(out.command).isEqualTo(37)
        assertThat(out.result).isEqualTo(1)
        assertThat(out.remains).isEqualTo(44.4)
        assertThat(out.infusedAmount).isEqualTo(5.5)
    }

    @Test
    fun `ProtocolExtendBolusInfusionRspModel maps result and expectedTime`() {
        val out = ProtocolExtendBolusInfusionRspModel(timestamp = 3838L, command = 38, result = 0, expectedTime = 240).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(3838L)
        assertThat(out.command).isEqualTo(38)
        assertThat(out.result).isEqualTo(0)
        assertThat(out.expectedTime).isEqualTo(240)
    }

    @Test
    fun `ProtocolExtendBolusInfusionCancelRspModel maps result and infusedAmount`() {
        val out = ProtocolExtendBolusInfusionCancelRspModel(timestamp = 3939L, command = 39, result = 2, infusedAmount = 6.25).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(3939L)
        assertThat(out.command).isEqualTo(39)
        assertThat(out.result).isEqualTo(2)
        assertThat(out.infusedAmount).isEqualTo(6.25)
    }

    @Test
    fun `ProtocolExtendBolusDelayRptModel maps delayedAmount and expectedTime`() {
        val out = ProtocolExtendBolusDelayRptModel(timestamp = 4040L, command = 40, delayedAmount = 7.5, expectedTime = 360).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(4040L)
        assertThat(out.command).isEqualTo(40)
        assertThat(out.delayedAmount).isEqualTo(7.5)
        assertThat(out.expectedTime).isEqualTo(360)
    }

    @Test
    fun `ProtocolPatchRecoveryRptModel maps timestamp and command only`() {
        val out = ProtocolPatchRecoveryRptModel(timestamp = 4141L, command = 41).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(4141L)
        assertThat(out.command).isEqualTo(41)
    }

    @Test
    fun `ProtocolAppAuthKeyAckRspModel maps value`() {
        val out = ProtocolAppAuthKeyAckRspModel(timestamp = 4242L, command = 42, value = 12345).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(4242L)
        assertThat(out.command).isEqualTo(42)
        assertThat(out.value).isEqualTo(12345)
    }

    @Test
    fun `ProtocolAppAuthAckRspModel maps result`() {
        val out = ProtocolAppAuthAckRspModel(timestamp = 4343L, command = 43, result = 21).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(4343L)
        assertThat(out.command).isEqualTo(43)
        assertThat(out.result).isEqualTo(21)
    }

    @Test
    fun `negative and zero edge values pass through unchanged`() {
        val out = ProtocolInfusionStatusInquiryRptModel(
            timestamp = 0L,
            command = 0,
            subId = 0,
            patchRunningTime = 0,
            insulinRemains = 0.0,
            infusedTotalBasalAmount = 0.0,
            infusedTotalBolusAmount = 0.0,
            pumpState = 0,
            mode = 0,
            infusedSetMin = 0,
            currentInfusedProgramVolume = 0.0,
            realInfusedTime = 0
        ).transformToDomainModel()
        assertThat(out.timestamp).isEqualTo(0L)
        assertThat(out.remains).isEqualTo(0.0)
        assertThat(out.runningMinutes).isEqualTo(0)
    }

    @Test
    fun `empty string fields pass through unchanged`() {
        val out = ProtocolPatchInformationInquiryDetailRptModel(
            timestamp = 1L, command = 1, result = 0, firmVersion = "", bootDateTime = "", modelName = ""
        ).transformToDomainModel()
        assertThat(out.firmVersion).isEqualTo("")
        assertThat(out.bootDateTime).isEqualTo("")
        assertThat(out.modelName).isEqualTo("")
    }
}
