package info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt

import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RepositoryRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.basal.CarelevoBasalSegment

internal interface TempBasalRequest

data class SetTimeRequest(
    val dateTime : String,
    val volume : Int,
    val subId : Int,
    val aidMode : Int
) : RepositoryRequest

data class SetBuzzModeRequest(
    val isOn : Boolean
) : RepositoryRequest

data class ThresholdSetRequest(
    val remains : Int,
    val expiryHour : Int,
    val maxBasalSpeed : Double,
    val maxBolusDose : Double,
    val buzzUse : Boolean
) : RepositoryRequest

data class SetAlertAlarmModeRequest(
    val mode : Int
) : RepositoryRequest

data class SetExpiryExtendRequest(
    val extendHour : Int
) : RepositoryRequest

data class StopPumpRequest(
    val expectMinutes : Int,
    val subId : Int
) : RepositoryRequest

data class ResumePumpRequest(
    val mode : Int,
    val causeId : Int
) : RepositoryRequest

data class StopPumpRptAckRequest(
    val subId : Int
) : RepositoryRequest

data class SetThresholdInfusionMaxSpeedRequest(
    val value : Double
) : RepositoryRequest

data class SetThresholdNoticeRequest(
    val value : Int,
    val type : Int
) : RepositoryRequest

data class SetThresholdInfusionMaxDoseRequest(
    val value : Double
) : RepositoryRequest

data class RetrieveInfusionStatusRequest(
    val inquiryType : Int
) : RepositoryRequest

data class SetApplicationStatusRequest(
    val isBackground : Boolean,
    val infusionStopHour : Int
) : RepositoryRequest

data class SetAlarmClearRequest(
    val alarmType : Int,
    val causeId : Int
) : RepositoryRequest

data class SetInitializeRequest(
    val mode : Boolean
) : RepositoryRequest

data class RetrieveAddressRequest(
    val key : Byte
) : RepositoryRequest

data class SetBasalProgramRequest(
    val totalSegmentCnt : Int,
    val segmentList : List<CarelevoBasalSegment>
) : RepositoryRequest

data class SetBasalProgramAdditionalRequest(
    val msgNumber : Int,
    val segmentCnt : Int,
    val segmentList : List<CarelevoBasalSegment>
) : RepositoryRequest

data class SetBasalProgramRequestV2(
    val seqNo : Int,
    val segmentList : List<CarelevoBasalSegment>
) : RepositoryRequest

data class UpdateBasalProgramRequest(
    val totalBasalSegmentCnt : Int,
    val segmentList : List<CarelevoBasalSegment>
) : RepositoryRequest

data class UpdateBasalProgramAdditionalRequest(
    val msgNumber : Int,
    val segmentCnt : Int,
    val segmentList : List<CarelevoBasalSegment>
) : RepositoryRequest

data class StartTempBasalProgramByUnitRequest(
    val infusionUnit : Double,
    val infusionHour : Int,
    val infusionMin : Int
) : RepositoryRequest

data class StartTempBasalProgramByPercentRequest(
    val infusionPercent : Int,
    val infusionHour : Int,
    val infusionMin : Int
) : RepositoryRequest

data class StartImmeBolusRequest(
    val actionId : Int,
    val volume : Double,
) : RepositoryRequest

data class StartExtendBolusRequest(
    val volume : Double,
    val speed : Double,
    val hour : Int,
     val min : Int
) : RepositoryRequest