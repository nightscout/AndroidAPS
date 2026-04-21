package info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt

import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RepositoryResponse

interface BtResponse : RepositoryResponse {

    val timestamp: Long
    val command: Int
}

data class SetTimeResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : BtResponse

data class SafetyCheckResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int,
    val volume: Int,
    val durationSeconds: Int
) : BtResponse

data class AdditionalPrimingResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : BtResponse

data class SetAlertAlarmModelResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : BtResponse

data class SetThresholdNoticeResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int,
    val type: Int
) : BtResponse

data class SetInfusionThresholdResponse(
    override val timestamp: Long,
    override val command: Int,
    val type: Int,
    val result: Int
) : BtResponse

data class SetBuzzModeResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : BtResponse

data class CannulaInsertionResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : BtResponse

data class CannulaInsertionAckResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : BtResponse

data class ThresholdSetResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : BtResponse

data class SetExpiryExtendResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : BtResponse

data class StopPumpResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : BtResponse

data class ResumePumpResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int,
    val mode: Int,
    val causeId: Int
) : BtResponse

data class StopPumpReportResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int,
    val mode: Int,
    val causeId: Int,
    val infusedBolusAmount: Double,
    val unInfusedExtendBolusAmount: Double,
    val temperature: Int
) : BtResponse

data class PatchInformationInquiryResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int,
    // val productCL : String,
    // val productTY : String,
    // val productMO : String,
    // val processCO : String,
    // val manufactureYE : String,
    // val manufactureMO : String,
    // val manufactureDA : String,
    // val manufactureLO : String,
    // val manufactureNO : String
    val serialNum: String
) : BtResponse

data class PatchInformationInquiryDetailResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int,
    val firmVersion: String,
    val bootDateTime: String,
    val modelName: String
) : BtResponse

data class RetrieveInfusionStatusResponse(
    override val timestamp: Long,
    override val command: Int,
    val subId: Int,
    val runningMinutes: Int,
    val remains: Double,
    val infusedTotalBasalAmount: Double,
    val infusedTotalBolusAmount: Double,
    val pumpState: Int,
    val mode: Int,
    val infusedSetMinutes: Int,
    val currentInfusedProgramVolume: Double,
    val realInfusedTime: Int
) : BtResponse

data class SetApplicationStatusResponse(
    override val timestamp: Long,
    override val command: Int,
    val status: Int
) : BtResponse

data class SetInitializeResponse(
    override val timestamp: Long,
    override val command: Int,
    val mode: Int
) : BtResponse

data class SetDiscardResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : BtResponse

data class CheckBuzzResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : BtResponse

data class RetrieveOperationInfoResponse(
    override val timestamp: Long,
    override val command: Int,
    val mode: Int,
    val pulseCnt: Int,
    val totalNo: Int,
    val count: Int,
    val useMinutes: Int,
    val remains: Double
) : BtResponse

data class RetrieveAddressResponse(
    override val timestamp: Long,
    override val command: Int,
    // val value : Int,
    val address: String,
    val checkSum: String,
) : BtResponse

data class WarningReportResponse(
    override val timestamp: Long,
    override val command: Int,
    val cause: Int,
    val value: Int,
) : BtResponse

data class AlertReportResponse(
    override val timestamp: Long,
    override val command: Int,
    val cause: Int,
    val value: Int
) : BtResponse

data class NoticeReportResponse(
    override val timestamp: Long,
    override val command: Int,
    val cause: Int,
    val value: Int
) : BtResponse

data class ClearReportResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int,
    val subId: Int,
    val cause: Int
) : BtResponse

data class RecoveryPatchResponse(
    override val timestamp: Long,
    override val command: Int,
) : BtResponse

data class AppAuthRptResponse(
    override val timestamp: Long,
    override val command: Int,
    val value: Int
) : BtResponse

data class AppAuthAckRptResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : BtResponse

data class SetBasalProgramResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : BtResponse

data class SetBasalProgramAdditionalResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : BtResponse

data class UpdateBasalProgramResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : BtResponse

data class UpdateBasalProgramAdditionalResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : BtResponse

data class StartTempBasalProgramResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : BtResponse

data class ResumeBasalProgramResponse(
    override val timestamp: Long,
    override val command: Int,
    val segmentNo: Int,
    val infusionSpeed: Double,
    val infusionPeriod: Int,
    val insulinRemains: Double
) : BtResponse

data class StartBasalProgramResponse(
    override val timestamp: Long,
    override val command: Int
) : BtResponse

data class CancelTempBasalProgramResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : BtResponse

data class StartImmeBolusResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int,
    val actionId: Int,
    val expectedTime: Int,
    val remain: Double
) : BtResponse

data class CancelImmeBolusResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int,
    val remains: Double,
    val infusedAmount: Double
) : BtResponse

data class StartExtendBolusResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int,
    val expectedTime: Int
) : BtResponse

data class CancelExtendBolusResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int,
    val infusedAmount: Double
) : BtResponse

data class DelayExtendBolusResponse(
    override val timestamp: Long,
    override val command: Int,
    val delayedAmount: Double,
    val expectedTime: Int
) : BtResponse

data class AppAlarmOffResponse(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : BtResponse