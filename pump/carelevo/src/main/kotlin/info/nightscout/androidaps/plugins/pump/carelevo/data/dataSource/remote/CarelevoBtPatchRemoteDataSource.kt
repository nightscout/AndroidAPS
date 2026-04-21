package info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.remote

import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.CommandResult
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.BleResponse
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolRspModel
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single

interface CarelevoBtPatchRemoteDataSource {

    fun getPatchResponse() : Observable<BleResponse<ProtocolRspModel>>

    fun setTime(dateTime : String, volume : Int, subId : Int, aidMode : Int) : Single<CommandResult<Boolean>>
    fun manipulateSafetyCheckStart() : Single<CommandResult<Boolean>>
    fun setExpiryExtend(extendHour : Int) : Single<CommandResult<Boolean>>
    fun setAppAuth(key : Byte) : Single<CommandResult<Boolean>>
    fun setAppAuthAck(isSuccess : Boolean) : Single<CommandResult<Boolean>>

    fun setThresholdNotice(value : Int, type : Int) : Single<CommandResult<Boolean>>
    fun setThresholdInsulinMaxSpeed(value : Double) : Single<CommandResult<Boolean>>
    fun setThresholdInsulinMaxVolume(value : Double) : Single<CommandResult<Boolean>>
    fun setThreshold(
        insulinRemainsThreshold : Int,
        expiryThreshold : Int,
        maxBasalSpeed : Double,
        maxBolusDose : Double,
        buzzUse : Boolean
    ) : Single<CommandResult<Boolean>>
    fun setBuzzMode(use : Boolean) : Single<CommandResult<Boolean>>
    fun setInfusionThreshold(isBasal : Boolean, threshold : Double) : Single<CommandResult<Boolean>>
    fun setThresholdSet() : Single<CommandResult<Boolean>>
    fun setApplicationStatus(isAppBackground : Boolean, hour : Int) : Single<CommandResult<Boolean>>
    fun setAlarmMode(mode : Int) : Single<CommandResult<Boolean>>

    fun retrieveCannulaStatus() : Single<CommandResult<Boolean>>
    fun retrieveInfusionStatusInformation(inquiryType : Int) : Single<CommandResult<Boolean>>
    fun retrievePatchDeviceInformation() : Single<CommandResult<Boolean>>
    fun retrievePatchOperationInformation() : Single<CommandResult<Boolean>>
    fun retrieveThresholds() : Single<CommandResult<Boolean>>
    fun retrieveMacAddress(key : Byte) : Single<CommandResult<Boolean>>

    fun manipulatePumpStop(min : Int, subId : Int) : Single<CommandResult<Boolean>>
    fun manipulatePumpResume(mode : Int, subId : Int) : Single<CommandResult<Boolean>>
    fun manipulatePumpStopAck(subId : Int) : Single<CommandResult<Boolean>>
    fun manipulateDiscardPatch() : Single<CommandResult<Boolean>>
    fun manipulateBuzzRunning() : Single<CommandResult<Boolean>>
    fun manipulateInitialize(mode : Boolean) : Single<CommandResult<Boolean>>
    fun manipulateClearAlarm(alarmType : Int, cause : Int) : Single<CommandResult<Boolean>>
    fun manipulateAdditionalPriming() : Single<CommandResult<Boolean>>

    fun confirmReportCannulaInsertion(isSuccess : Boolean) : Single<CommandResult<Boolean>>
    fun confirmPatchRecovery() : Single<CommandResult<Boolean>>
}