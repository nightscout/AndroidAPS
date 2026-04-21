package info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble

internal interface ProtocolRequestModel

data class ProtocolSegmentModel(
    val injectHour: Int,
    val injectMin: Int,
    val injectSpeed: Double
) : ProtocolRequestModel

interface ProtocolRspModel {

    val timestamp: Long
    val command: Int
}

data class ProtocolSetTimeRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : ProtocolRspModel

data class ProtocolAppAuthKeyAckRspModel(
    override val timestamp: Long,
    override val command: Int,
    val value: Int
) : ProtocolRspModel

data class ProtocolAppAuthAckRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : ProtocolRspModel

data class ProtocolSafetyCheckRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int,
    val insulinVolume: Int,
    val durationSeconds: Int
) : ProtocolRspModel

data class ProtocolInfusionThresholdRspModel(
    override val timestamp: Long,
    override val command: Int,
    val type: Int,
    val result: Int
) : ProtocolRspModel

data class ProtocolBuzzUsageChangeRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : ProtocolRspModel

data class ProtocolCannulaInsertionStatusRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : ProtocolRspModel

data class ProtocolCannulaInsertionAckRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : ProtocolRspModel

data class ProtocolPatchThresholdSetRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : ProtocolRspModel

data class ProtocolPatchAlertAlarmSetRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : ProtocolRspModel

data class ProtocolNoticeThresholdRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int,
    val type: Int
) : ProtocolRspModel

data class ProtocolPatchExpiryExtendRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : ProtocolRspModel

data class ProtocolPumpStopRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : ProtocolRspModel

data class ProtocolPumpResumeRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int,
    val mode: Int,
    val subId: Int
) : ProtocolRspModel

data class ProtocolPumpStopRptModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int,
    val cause: Int,
    val mode: Int,
    val subId: Int,
    val completedBolusInfusionVolume: Double,
    val unInfusedExtendBolusVolume: Double,
    val temperature: Int
) : ProtocolRspModel

data class ProtocolInfusionStatusInquiryRptModel(
    override val timestamp: Long,
    override val command: Int,
    val subId: Int,
    val patchRunningTime: Int,
    val insulinRemains: Double,
    val infusedTotalBasalAmount: Double,
    val infusedTotalBolusAmount: Double,
    val pumpState: Int,
    val mode: Int,
    val infusedSetMin: Int,
    val currentInfusedProgramVolume: Double,
    val realInfusedTime: Int
) : ProtocolRspModel

data class ProtocolPatchInformationInquiryRptModel(
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
    // val manufactureNO : String,
    val serialNum: String
) : ProtocolRspModel

data class ProtocolPatchInformationInquiryDetailRptModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int,
    val firmVersion: String,
    val bootDateTime: String,
    val modelName: String
) : ProtocolRspModel

data class ProtocolThresholdRetrieveRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int,
    val insulinDeficiencyAlarmThreshold: Int,
    val expiryAlarmThreshold: Int
) : ProtocolRspModel

data class ProtocolPatchDiscardRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : ProtocolRspModel

data class ProtocolPatchBuzzInspectionRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : ProtocolRspModel

data class ProtocolPatchOperationDataRspModel(
    override val timestamp: Long,
    override val command: Int,
    val mode: Int,
    val pulseCnt: Int,
    val totalNo: Int,
    val count: Int,
    val useMin: Int,
    val remains: Double
) : ProtocolRspModel

data class ProtocolAppStatusRspModel(
    override val timestamp: Long,
    override val command: Int,
    val status: Int
) : ProtocolRspModel

data class ProtocolGlucoseMeasurementAlarmTimerRspModel(
    override val timestamp: Long,
    override val command: Int,
    val timerId: Int,
    val minutes: Int
) : ProtocolRspModel

data class ProtocolGlucoseTimerForCGMRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int,
    val triggerType: Int
) : ProtocolRspModel

data class ProtocolGlucoseTimerRptModel(
    override val timestamp: Long,
    override val command: Int
) : ProtocolRspModel

data class ProtocolPatchAddressRspModel(
    override val timestamp: Long,
    override val command: Int,
    // val value : Int,
    val macAddress: String,
    val checkSum: String
) : ProtocolRspModel

data class ProtocolWarningMsgRptModel(
    override val timestamp: Long,
    override val command: Int,
    val cause: Int,
    val value: Int
) : ProtocolRspModel

data class ProtocolAlertMsgRptModel(
    override val timestamp: Long,
    override val command: Int,
    val cause: Int,
    val value: Int
) : ProtocolRspModel

data class ProtocolNoticeMsgRptModel(
    override val timestamp: Long,
    override val command: Int,
    val cause: Int,
    val value: Int
) : ProtocolRspModel

data class ProtocolMsgSolutionRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int,
    val subId: Int,
    val cause: Int
) : ProtocolRspModel

data class ProtocolPatchInitRspModel(
    override val timestamp: Long,
    override val command: Int,
    val mode: Int
) : ProtocolRspModel

data class ProtocolPatchRecoveryRptModel(
    override val timestamp: Long,
    override val command: Int
) : ProtocolRspModel

data class ProtocolAdditionalPrimingRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : ProtocolRspModel

data class ProtocolBasalProgramSetRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : ProtocolRspModel

data class ProtocolAdditionalBasalProgramSetRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : ProtocolRspModel

data class ProtocolBasalInfusionChangeRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : ProtocolRspModel

data class ProtocolAdditionalBasalInfusionChangeRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : ProtocolRspModel

data class ProtocolBasalInfusionResumeRspModel(
    override val timestamp: Long,
    override val command: Int,
    val segmentNo: Int,
    val infusionSpeed: Double,
    val infusionPeriod: Int,
    val insulinRemains: Double
) : ProtocolRspModel

data class ProtocolTempBasalInfusionRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : ProtocolRspModel

data class ProtocolBasalInfusionStartRspModel(
    override val timestamp: Long,
    override val command: Int
) : ProtocolRspModel

data class ProtocolTempBasalInfusionCancelRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int
) : ProtocolRspModel

data class ProtocolImmeBolusInfusionRspModel(
    override val timestamp: Long,
    override val command: Int,
    val actionId: Int,
    val result: Int,
    val expectedTime: Int,
    val remains: Double
) : ProtocolRspModel

data class ProtocolExtendBolusInfusionRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int,
    val expectedTime: Int
) : ProtocolRspModel

data class ProtocolExtendBolusInfusionCancelRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int,
    val infusedAmount: Double
) : ProtocolRspModel

data class ProtocolBolusInfusionCancelRspModel(
    override val timestamp: Long,
    override val command: Int,
    val result: Int,
    val insulinRemains: Double,
    val infusedAmount: Double
) : ProtocolRspModel

data class ProtocolExtendBolusDelayRptModel(
    override val timestamp: Long,
    override val command: Int,
    val delayedAmount: Double,
    val expectedTime: Int
) : ProtocolRspModel