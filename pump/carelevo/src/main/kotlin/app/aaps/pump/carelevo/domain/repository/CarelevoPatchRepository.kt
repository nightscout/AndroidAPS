package app.aaps.pump.carelevo.domain.repository

import app.aaps.pump.carelevo.domain.model.RequestResult
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.bt.BtResponse
import app.aaps.pump.carelevo.domain.model.bt.ResumePumpRequest
import app.aaps.pump.carelevo.domain.model.bt.RetrieveAddressRequest
import app.aaps.pump.carelevo.domain.model.bt.RetrieveInfusionStatusRequest
import app.aaps.pump.carelevo.domain.model.bt.SetAlarmClearRequest
import app.aaps.pump.carelevo.domain.model.bt.SetAlertAlarmModeRequest
import app.aaps.pump.carelevo.domain.model.bt.SetApplicationStatusRequest
import app.aaps.pump.carelevo.domain.model.bt.SetBuzzModeRequest
import app.aaps.pump.carelevo.domain.model.bt.SetExpiryExtendRequest
import app.aaps.pump.carelevo.domain.model.bt.SetInitializeRequest
import app.aaps.pump.carelevo.domain.model.bt.SetThresholdInfusionMaxDoseRequest
import app.aaps.pump.carelevo.domain.model.bt.SetThresholdInfusionMaxSpeedRequest
import app.aaps.pump.carelevo.domain.model.bt.SetThresholdNoticeRequest
import app.aaps.pump.carelevo.domain.model.bt.SetTimeRequest
import app.aaps.pump.carelevo.domain.model.bt.StopPumpRequest
import app.aaps.pump.carelevo.domain.model.bt.StopPumpRptAckRequest
import app.aaps.pump.carelevo.domain.model.bt.ThresholdSetRequest
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single

interface CarelevoPatchRepository {

    fun getResponseResult(): Observable<ResponseResult<BtResponse>>

    fun requestSetTime(param: SetTimeRequest): Single<RequestResult<Boolean>>
    fun requestExtendExpiry(param: SetExpiryExtendRequest): Single<RequestResult<Boolean>>
    fun requestSafetyCheck(): Single<RequestResult<Boolean>>
    fun requestSetThreshold(param: ThresholdSetRequest): Single<RequestResult<Boolean>>
    fun requestCannulaInsertionCheck(): Single<RequestResult<Boolean>>
    fun requestConfirmCannulaInsertionCheck(isSuccess: Boolean): Single<RequestResult<Boolean>>

    fun requestAppAuth(key: Byte): Single<RequestResult<Boolean>>
    fun requestAppAuthAck(isSuccess: Boolean): Single<RequestResult<Boolean>>

    fun requestSetThresholdNotice(param: SetThresholdNoticeRequest): Single<RequestResult<Boolean>>
    fun requestSetThresholdMaxSpeed(param: SetThresholdInfusionMaxSpeedRequest): Single<RequestResult<Boolean>>
    fun requestSetThresholdMaxDose(param: SetThresholdInfusionMaxDoseRequest): Single<RequestResult<Boolean>>

    fun requestSetBuzzMode(param: SetBuzzModeRequest): Single<RequestResult<Boolean>>
    fun requestCheckBuzz(): Single<RequestResult<Boolean>>
    fun requestSetApplicationStatus(param: SetApplicationStatusRequest): Single<RequestResult<Boolean>>

    fun requestRetrieveInfusionStatusInfo(param: RetrieveInfusionStatusRequest): Single<RequestResult<Boolean>>
    fun requestRetrieveDeviceInfo(): Single<RequestResult<Boolean>>
    fun requestRetrieveOperationInfo(): Single<RequestResult<Boolean>>
    fun requestRetrieveThreshold(): Single<RequestResult<Boolean>>
    fun requestRetrieveMacAddress(param: RetrieveAddressRequest): Single<RequestResult<Boolean>>

    fun requestStopPump(param: StopPumpRequest): Single<RequestResult<Boolean>>
    fun requestResumePump(param: ResumePumpRequest): Single<RequestResult<Boolean>>
    fun requestStopPumpAck(param: StopPumpRptAckRequest): Single<RequestResult<Boolean>>

    fun requestDiscardPatch(): Single<RequestResult<Boolean>>
    fun requestInitializePatch(param: SetInitializeRequest): Single<RequestResult<Boolean>>

    fun requestSetAlarmClear(param: SetAlarmClearRequest): Single<RequestResult<Boolean>>

    fun requestRecoveryPatchRptAck(): Single<RequestResult<Boolean>>
    fun requestAdditionalPriming(): Single<RequestResult<Boolean>>
    fun requestSetAlertAlarmMode(param: SetAlertAlarmModeRequest): Single<RequestResult<Boolean>>
}