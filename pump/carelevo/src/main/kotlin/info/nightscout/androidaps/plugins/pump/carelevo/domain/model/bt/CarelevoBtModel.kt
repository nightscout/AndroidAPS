package info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt

import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.InfusionInfoResult.Companion.codeToInfusionInfoCommand
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.InfusionModeResult.Companion.codeToInfusionModeCommand
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.PumpStateResult.Companion.codeToPumpStateCommand
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.Result.Companion.codeToResultCommand
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SafetyCheckResult.Companion.codeToSafetyCheckCommand
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SetBasalProgramResult.Companion.codeToSetBasalProgramCommand
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SetBolusProgramResult.Companion.codeToSetBolusProgramCommand
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.StopPumpResult.Companion.codeToStopPumpCommand
import info.nightscout.androidaps.plugins.pump.carelevo.domain.type.AlarmCause
import info.nightscout.androidaps.plugins.pump.carelevo.domain.type.AlarmType

internal interface PatchResultModel

data class ProtocolFailedAlarmMode(val alarmId: Long, val cause: Int) : PatchResultModel

data class SetTimeResultModel(
    val result: Result
) : PatchResultModel

data class SafetyCheckResultModel(
    val result: SafetyCheckResult,
    val volume: Int,
    val durationSeconds: Int
) : PatchResultModel

data class AdditionalPrimingResultModel(
    val result: Result
) : PatchResultModel

data class SetThresholdNoticeResultModel(
    val type: Int,
    val result: Result
) : PatchResultModel

data class SetInfusionThresholdResultModel(
    val result: Result,
    val type: Int
) : PatchResultModel

data class SetBuzzModeResultModel(
    val result: Result
) : PatchResultModel

data class SetAlarmClearResultModel(
    val result: Result,
    val subId: Int,
    val cause: Int
) : PatchResultModel

data class CannulaInsertionResultModel(
    val result: Result
) : PatchResultModel

data class CannulaInsertionAckResultModel(
    val result: Result
) : PatchResultModel

data class ThresholdSetResultModel(
    val result: Result
) : PatchResultModel

data class ExtendPatchExpiryResultModel(
    val result: Result
) : PatchResultModel

data class StopPumpResultModel(
    val result: Result
) : PatchResultModel

data class ResumePumpResultModel(
    val result: StopPumpResult,
    val mode: InfusionModeResult,
    val subId: Int
) : PatchResultModel

data class StopPumpReportResultModel(
    val result: StopPumpResult,
    val mode: InfusionModeResult,
    val subId: Int,
    val infusedBolusInfusionAmount: Double,
    val infusedBasalInfusionAmount: Double,
    val temperature: Int
) : PatchResultModel

data class InfusionInfoReportResultModel(
    val subId: InfusionInfoResult,
    val runningMinutes: Int,
    val remains: Double,
    val infusedTotalBasalAmount: Double,
    val infusedTotalBolusAmount: Double,
    val pumpState: PumpStateResult,
    val mode: InfusionModeResult,
    val infuseSetMinutes: Int,
    val currentInfusedProgramVolume: Double,
    val realInfusedTime: Int
) : PatchResultModel

data class PatchInformationInquiryModel(
    val result: Result,
    // val productCL : String,
    // val productTY : String,
    // val productMO : String,
    // val processCO : String,
    // val manufactureYE : String,
    // val manufactureMO : String,
    // val manufactureDA : String,
    // val manufactureLO : String,
    // val manufactureNO : String,
    val serialNum: String
) : PatchResultModel

data class PatchInformationInquiryDetailModel(
    val result: Result,
    val firmwareVer: String,
    val bootDateTime: String,
    val modelName: String
) : PatchResultModel

data class DiscardPatchResultModel(
    val result: Result
) : PatchResultModel

data class CheckBuzzResultModel(
    val result: Result
) : PatchResultModel

data class FinishPulseReportResultModel(
    val mode: InfusionModeResult,
    val pulseCnt: Int,
    val totalNo: Int,
    val count: Int,
    val useMinutes: Int,
    val remains: Double
) : PatchResultModel

data class SetApplicationStatusResultModel(
    val status: Int
) : PatchResultModel

data class RetrieveAddressResultModel(
    // val value : Int,
    val address: String,
    val checkSum: String
) : PatchResultModel

class RecoveryPatchReportResultModel() : PatchResultModel

data class WarningReportResultModel(
    val cause: AlarmCause,
    val value: Int
) : PatchResultModel

data class AlertReportResultModel(
    val cause: AlarmCause,
    val value: Int
) : PatchResultModel

data class NoticeReportResultModel(
    val cause: AlarmCause,
    val value: Int
) : PatchResultModel

data class AppAuthAckReportResultModel(
    val value: Int
) : PatchResultModel

data class AlertAlarmSetResultModel(
    val result: Result
) : PatchResultModel

data class AppAuthAckResultModel(
    val result: Result
) : PatchResultModel

data class AppAlarmClearResultModel(
    val result: Result
) : PatchResultModel

data class AppBuzzResultModel(
    val result: Result
) : PatchResultModel

internal fun createPatchResultModel(response: BtResponse): PatchResultModel? {
    return if (isPatchProtocol(response.command) && response is SetTimeResponse) {
        val value = response.result.codeToResultCommand()
        SetTimeResultModel(value)
    } else if (isPatchProtocol(response.command) && response is PatchInformationInquiryResponse) {
        val value = response.result.codeToResultCommand()
        PatchInformationInquiryModel(
            value,
            // response.productCL,
            // response.productTY,
            // response.productMO,
            // response.processCO,
            // response.manufactureYE,
            // response.manufactureMO,
            // response.manufactureDA,
            // response.manufactureLO,
            // response.manufactureNO
            response.serialNum
        )
    } else if (isPatchProtocol(response.command) && response is PatchInformationInquiryDetailResponse) {
        val value = response.result.codeToResultCommand()
        PatchInformationInquiryDetailModel(value, response.firmVersion, response.bootDateTime, response.modelName)
    } else if (isPatchProtocol(response.command) && response is SafetyCheckResponse) {
        val value = response.result.codeToSafetyCheckCommand()
        SafetyCheckResultModel(value, response.volume, response.durationSeconds)
    } else if (isPatchProtocol(response.command) && response is ThresholdSetResponse) {
        val value = response.result.codeToResultCommand()
        ThresholdSetResultModel(value)
    } else if (isPatchProtocol(response.command) && response is CannulaInsertionResponse) {
        val value = response.result.codeToResultCommand()
        CannulaInsertionResultModel(value)
    } else if (isPatchProtocol(response.command) && response is CannulaInsertionAckResponse) {
        val value = response.result.codeToResultCommand()
        CannulaInsertionAckResultModel(value)
    } else if (isPatchProtocol(response.command) && response is SetInfusionThresholdResponse) {
        val value = response.result.codeToResultCommand()
        SetInfusionThresholdResultModel(value, response.type)
    } else if (isPatchProtocol(response.command) && response is SetBuzzModeResponse) {
        val value = response.result.codeToResultCommand()
        SetBuzzModeResultModel(value)
    } else if (isPatchProtocol(response.command) && response is ClearReportResponse) {
        val value = response.result.codeToResultCommand()
        SetAlarmClearResultModel(value, response.subId, response.cause)
    } else if (isPatchProtocol(response.command) && response is SetExpiryExtendResponse) {
        val value = response.result.codeToResultCommand()
        ExtendPatchExpiryResultModel(value)
    } else if (isPatchProtocol(response.command) && response is StopPumpResponse) {
        val value = response.result.codeToResultCommand()
        StopPumpResultModel(value)
    } else if (isPatchProtocol(response.command) && response is ResumePumpResponse) {
        val value = response.result.codeToStopPumpCommand()
        val mode = response.mode.codeToInfusionModeCommand()
        ResumePumpResultModel(value, mode, response.causeId)
    } else if (isPatchProtocol(response.command) && response is StopPumpReportResponse) {
        val value = response.result.codeToStopPumpCommand()
        val mode = response.mode.codeToInfusionModeCommand()
        StopPumpReportResultModel(
            value,
            mode,
            response.causeId,
            response.infusedBolusAmount,
            response.unInfusedExtendBolusAmount,
            response.temperature
        )
    } else if (isPatchProtocol(response.command) && response is RetrieveInfusionStatusResponse) {
        val subId = response.subId.codeToInfusionInfoCommand()
        val pumpState = response.pumpState.codeToPumpStateCommand()
        val mode = response.mode.codeToInfusionModeCommand()
        InfusionInfoReportResultModel(
            subId,
            response.runningMinutes,
            response.remains,
            response.infusedTotalBasalAmount,
            response.infusedTotalBolusAmount,
            pumpState,
            mode,
            response.infusedSetMinutes,
            response.currentInfusedProgramVolume,
            response.realInfusedTime
        )
    } else if (isPatchProtocol(response.command) && response is SetApplicationStatusResponse) {
        SetApplicationStatusResultModel(response.status)
    } else if (isPatchProtocol(response.command) && response is RetrieveAddressResponse) {
        RetrieveAddressResultModel(
            // response.value,
            response.address,
            response.checkSum
        )
    } else if (isPatchProtocol(response.command) && response is SetDiscardResponse) {
        val value = response.result.codeToResultCommand()
        DiscardPatchResultModel(value)
    } else if (isPatchProtocol(response.command) && response is RecoveryPatchResponse) {
        RecoveryPatchReportResultModel()
    } else if (isPatchProtocol(response.command) && response is WarningReportResponse) {
        WarningReportResultModel(
            //response.cause.codeToWarningMessageCommand(),
            AlarmCause.fromTypeAndCode(AlarmType.WARNING, response.cause),
            response.value
        )
    } else if (isPatchProtocol(response.command) && response is AlertReportResponse) {
        AlertReportResultModel(
            AlarmCause.fromTypeAndCode(AlarmType.ALERT, response.cause),
            response.value
        )
    } else if (isPatchProtocol(response.command) && response is NoticeReportResponse) {
        NoticeReportResultModel(
            AlarmCause.fromTypeAndCode(AlarmType.NOTICE, response.cause),
            response.value
        )
    } else if (isPatchProtocol(response.command) && response is AppAuthRptResponse) {
        AppAuthAckReportResultModel(response.value)
    } else if (isPatchProtocol(response.command) && response is AdditionalPrimingResponse) {
        AdditionalPrimingResultModel(response.result.codeToResultCommand())
    } else if (isPatchProtocol(response.command) && response is SetThresholdNoticeResponse) {
        SetThresholdNoticeResultModel(
            response.type,
            response.result.codeToResultCommand()
        )
    } else if (isPatchProtocol(response.command) && response is SetAlertAlarmModelResponse) {
        AlertAlarmSetResultModel(response.result.codeToResultCommand())
    } else if (isPatchProtocol(response.command) && response is AppAuthAckRptResponse) {
        AppAuthAckResultModel(response.result.codeToResultCommand())
    } else if (isPatchProtocol(response.command) && response is AppAlarmOffResponse) {
        AppAlarmClearResultModel(response.result.codeToResultCommand())
    } else if (isPatchProtocol(response.command) && response is RetrieveOperationInfoResponse) {
        RetrieveOperationInfoResultModel(
            mode = response.mode,
            pulseCnt = response.pulseCnt,
            totalNo = response.totalNo,
            count = response.count,
            useMinutes = response.useMinutes,
            remains = response.remains
        )
    } else if (isPatchProtocol(response.command) && response is CheckBuzzResponse) {
        AppBuzzResultModel(response.result.codeToResultCommand())
    } else {
        null
    }
}

data class SetBasalProgramResultModel(
    val result: SetBasalProgramResult
) : PatchResultModel

data class SetBasalProgramAdditionalResultModel(
    val result: SetBasalProgramResult
) : PatchResultModel

data class UpdateBasalProgramResultModel(
    val result: SetBasalProgramResult
) : PatchResultModel

data class UpdateBasalProgramAdditionalResultModel(
    val result: SetBasalProgramResult
) : PatchResultModel

data class StartTempBasalProgramResultModel(
    val result: SetBasalProgramResult
) : PatchResultModel

data class CancelTempBasalProgramResultModel(
    val result: Result
) : PatchResultModel

class StartBasalProgramResultModel() : PatchResultModel

data class BasalInfusionResumeResultModel(
    val segmentNo: Int,
    val infusionSpeed: Double,
    val infusionPeriod: Int,
    val insulinRemains: Double
) : PatchResultModel

internal fun createBasalResultModel(response: BtResponse): PatchResultModel? {
    return if (isBasalProtocol(response.command) && response is SetBasalProgramResponse) {
        val value = response.result.codeToSetBasalProgramCommand()
        SetBasalProgramResultModel(value)
    } else if (isBasalProtocol(response.command) && response is SetBasalProgramAdditionalResponse) {
        val value = response.result.codeToSetBasalProgramCommand()
        SetBasalProgramAdditionalResultModel(value)
    } else if (isBasalProtocol(response.command) && response is UpdateBasalProgramResponse) {
        val value = response.result.codeToSetBasalProgramCommand()
        UpdateBasalProgramResultModel(value)
    } else if (isBasalProtocol(response.command) && response is UpdateBasalProgramAdditionalResponse) {
        val value = response.result.codeToSetBasalProgramCommand()
        UpdateBasalProgramAdditionalResultModel(value)
    } else if (isBasalProtocol(response.command) && response is StartTempBasalProgramResponse) {
        val value = response.result.codeToSetBasalProgramCommand()
        StartTempBasalProgramResultModel(value)
    } else if (isBasalProtocol(response.command) && response is CancelTempBasalProgramResponse) {
        val value = response.result.codeToResultCommand()
        CancelTempBasalProgramResultModel(value)
    } else if (isBasalProtocol(response.command) && response is StartBasalProgramResponse) {
        StartBasalProgramResultModel()
    } else if (isBasalProtocol(response.command) && response is ResumeBasalProgramResponse) {
        BasalInfusionResumeResultModel(
            response.segmentNo,
            response.infusionSpeed,
            response.infusionPeriod,
            response.insulinRemains
        )
    } else {
        null
    }
}

data class StartImmeBolusResultModel(
    val result: SetBolusProgramResult,
    val actionId: Int,
    val expectedTime: Int,
    val remains: Double
) : PatchResultModel

data class CancelImmeBolusResultModel(
    val result: Result,
    val remains: Double,
    val infusedAmount: Double
) : PatchResultModel

data class StartExtendBolusResultModel(
    val result: SetBolusProgramResult,
    val expectedTime: Int
) : PatchResultModel

data class CancelExtendBolusResultModel(
    val result: Result,
    val infusedAmount: Double
) : PatchResultModel

data class DelayExtendBolusReportResultModel(
    val delayedAmount: Double,
    val expectedTime: Int
) : PatchResultModel

data class RetrieveOperationInfoResultModel(
    val mode: Int,
    val pulseCnt: Int,
    val totalNo: Int,
    val count: Int,
    val useMinutes: Int,
    val remains: Double
) : PatchResultModel

internal fun createBolusResultModel(response: BtResponse): PatchResultModel? {
    return if (isBolusProtocol(response.command) && response is StartImmeBolusResponse) {
        val value = response.result.codeToSetBolusProgramCommand()
        StartImmeBolusResultModel(
            value,
            response.actionId,
            response.expectedTime,
            response.remain
        )
    } else if (isBolusProtocol(response.command) && response is CancelImmeBolusResponse) {
        val value = response.result.codeToResultCommand()
        CancelImmeBolusResultModel(
            value,
            response.remains,
            response.infusedAmount
        )
    } else if (isBolusProtocol(response.command) && response is StartExtendBolusResponse) {
        val value = response.result.codeToSetBolusProgramCommand()
        StartExtendBolusResultModel(
            value,
            response.expectedTime
        )
    } else if (isBolusProtocol(response.command) && response is CancelExtendBolusResponse) {
        val value = response.result.codeToResultCommand()
        CancelExtendBolusResultModel(
            value,
            response.infusedAmount
        )
    } else if (isBolusProtocol(response.command) && response is DelayExtendBolusResponse) {
        DelayExtendBolusReportResultModel(
            response.delayedAmount,
            response.expectedTime
        )
    } else {
        null
    }
}