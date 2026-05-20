package app.aaps.pump.carelevo.data.repository

import app.aaps.pump.carelevo.ble.data.CommandResult
import app.aaps.pump.carelevo.data.dataSource.remote.CarelevoBtPatchRemoteDataSource
import app.aaps.pump.carelevo.data.mapper.transformToDomainModel
import app.aaps.pump.carelevo.data.model.ble.BleResponse
import app.aaps.pump.carelevo.data.model.ble.ProtocolAdditionalPrimingRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolAlertMsgRptModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolAppAuthAckRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolAppAuthKeyAckRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolAppStatusRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolBuzzUsageChangeRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolCannulaInsertionAckRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolCannulaInsertionStatusRspModel
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
import app.aaps.pump.carelevo.data.model.ble.ProtocolRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolSafetyCheckRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolSetTimeRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolWarningMsgRptModel
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
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchRepository
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import javax.inject.Inject

class CarelevoPatchRepositoryImpl @Inject constructor(
    private val btPatchRemoteDataSource: CarelevoBtPatchRemoteDataSource
) : CarelevoPatchRepository {

    override fun getResponseResult(): Observable<ResponseResult<BtResponse>> {
        return btPatchRemoteDataSource.getPatchResponse().map {
            when (it) {
                is BleResponse.RspResponse -> ResponseResult.Success(transformToDomainModel(it.data))
                is BleResponse.Error       -> ResponseResult.Error(it.e)
                is BleResponse.Failure     -> ResponseResult.Failure(it.message)
            }
        }
    }

    override fun requestSetTime(param: SetTimeRequest): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.setTime(
            dateTime = param.dateTime,
            volume = param.volume,
            subId = param.subId,
            aidMode = param.aidMode
        ).map {
            mapToResult(it)
        }
    }

    override fun requestExtendExpiry(param: SetExpiryExtendRequest): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.setExpiryExtend(
            extendHour = param.extendHour
        ).map {
            mapToResult(it)
        }
    }

    override fun requestSafetyCheck(): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.manipulateSafetyCheckStart().map { mapToResult(it) }
    }

    override fun requestSetThreshold(param: ThresholdSetRequest): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.setThreshold(
            insulinRemainsThreshold = param.remains,
            expiryThreshold = param.expiryHour,
            maxBasalSpeed = param.maxBasalSpeed,
            maxBolusDose = param.maxBolusDose,
            buzzUse = param.buzzUse
        ).map {
            mapToResult(it)
        }
    }

    override fun requestCannulaInsertionCheck(): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.retrieveCannulaStatus().map { mapToResult(it) }
    }

    override fun requestConfirmCannulaInsertionCheck(isSuccess: Boolean): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.confirmReportCannulaInsertion(isSuccess).map { mapToResult(it) }
    }

    override fun requestAppAuth(key: Byte): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.setAppAuth(key).map { mapToResult(it) }
    }

    override fun requestAppAuthAck(isSuccess: Boolean): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.setAppAuthAck(isSuccess).map { mapToResult(it) }
    }

    override fun requestSetThresholdNotice(param: SetThresholdNoticeRequest): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.setThresholdNotice(
            value = param.value,
            type = param.type
        ).map {
            mapToResult(it)
        }
    }

    override fun requestSetThresholdMaxSpeed(param: SetThresholdInfusionMaxSpeedRequest): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.setThresholdInsulinMaxSpeed(
            value = param.value
        ).map {
            mapToResult(it)
        }
    }

    override fun requestSetThresholdMaxDose(param: SetThresholdInfusionMaxDoseRequest): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.setThresholdInsulinMaxVolume(
            value = param.value
        ).map {
            mapToResult(it)
        }
    }

    override fun requestSetBuzzMode(param: SetBuzzModeRequest): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.setBuzzMode(
            use = param.isOn
        ).map {
            mapToResult(it)
        }
    }

    override fun requestCheckBuzz(): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.manipulateBuzzRunning().map { mapToResult(it) }
    }

    override fun requestSetApplicationStatus(param: SetApplicationStatusRequest): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.setApplicationStatus(
            isAppBackground = param.isBackground,
            hour = param.infusionStopHour
        ).map {
            mapToResult(it)
        }
    }

    override fun requestRetrieveInfusionStatusInfo(param: RetrieveInfusionStatusRequest): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.retrieveInfusionStatusInformation(
            inquiryType = param.inquiryType
        ).map {
            mapToResult(it)
        }
    }

    override fun requestRetrieveDeviceInfo(): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.retrievePatchDeviceInformation().map { mapToResult(it) }
    }

    override fun requestRetrieveOperationInfo(): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.retrievePatchOperationInformation().map { mapToResult(it) }
    }

    override fun requestRetrieveThreshold(): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.retrieveThresholds().map { mapToResult(it) }
    }

    override fun requestRetrieveMacAddress(param: RetrieveAddressRequest): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.retrieveMacAddress(
            key = param.key
        ).map {
            mapToResult(it)
        }
    }

    override fun requestStopPump(param: StopPumpRequest): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.manipulatePumpStop(
            min = param.expectMinutes,
            subId = param.subId
        ).map {
            mapToResult(it)
        }
    }

    override fun requestResumePump(param: ResumePumpRequest): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.manipulatePumpResume(
            mode = param.mode,
            subId = param.causeId
        ).map {
            mapToResult(it)
        }
    }

    override fun requestStopPumpAck(param: StopPumpRptAckRequest): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.manipulatePumpStopAck(
            subId = param.subId
        ).map {
            mapToResult(it)
        }
    }

    override fun requestDiscardPatch(): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.manipulateDiscardPatch().map { mapToResult(it) }
    }

    override fun requestInitializePatch(param: SetInitializeRequest): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.manipulateInitialize(
            mode = param.mode
        ).map {
            mapToResult(it)
        }
    }

    override fun requestSetAlarmClear(param: SetAlarmClearRequest): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.manipulateClearAlarm(
            alarmType = param.alarmType,
            cause = param.causeId
        ).map {
            mapToResult(it)
        }
    }

    override fun requestRecoveryPatchRptAck(): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.confirmPatchRecovery().map { mapToResult(it) }
    }

    override fun requestAdditionalPriming(): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.manipulateAdditionalPriming().map { mapToResult(it) }
    }

    override fun requestSetAlertAlarmMode(param: SetAlertAlarmModeRequest): Single<RequestResult<Boolean>> {
        return btPatchRemoteDataSource.setAlarmMode(
            mode = param.mode
        ).map {
            mapToResult(it)
        }
    }

    private fun mapToResult(result: CommandResult<Boolean>): RequestResult<Boolean> {
        return when (result) {
            is CommandResult.Pending -> RequestResult.Pending(result.data)
            is CommandResult.Success -> RequestResult.Success(result.data)
            is CommandResult.Error   -> RequestResult.Error(result.e)
            is CommandResult.Failure -> RequestResult.Failure(result.message)
        }
    }

    private fun transformToDomainModel(source: ProtocolRspModel): BtResponse? {
        return when (source) {
            is ProtocolSetTimeRspModel                       -> source.transformToDomainModel()
            is ProtocolSafetyCheckRspModel                   -> source.transformToDomainModel()
            is ProtocolPatchInformationInquiryRptModel       -> source.transformToDomainModel()
            is ProtocolPatchInformationInquiryDetailRptModel -> source.transformToDomainModel()
            is ProtocolPatchThresholdSetRspModel             -> source.transformToDomainModel()
            is ProtocolCannulaInsertionStatusRspModel        -> source.transformToDomainModel()
            is ProtocolCannulaInsertionAckRspModel           -> source.transformToDomainModel()
            is ProtocolBuzzUsageChangeRspModel               -> source.transformToDomainModel()
            is ProtocolPatchExpiryExtendRspModel             -> source.transformToDomainModel()
            is ProtocolPumpStopRspModel                      -> source.transformToDomainModel()
            is ProtocolPumpResumeRspModel                    -> source.transformToDomainModel()
            is ProtocolPumpStopRptModel                      -> source.transformToDomainModel()
            is ProtocolInfusionStatusInquiryRptModel         -> source.transformToDomainModel()
            is ProtocolPatchDiscardRspModel                  -> source.transformToDomainModel()
            is ProtocolPatchBuzzInspectionRspModel           -> source.transformToDomainModel()
            is ProtocolPatchOperationDataRspModel            -> source.transformToDomainModel()
            is ProtocolAppStatusRspModel                     -> source.transformToDomainModel()
            is ProtocolPatchAddressRspModel                  -> source.transformToDomainModel()
            is ProtocolWarningMsgRptModel                    -> source.transformToDomainModel()
            is ProtocolAlertMsgRptModel                      -> source.transformToDomainModel()
            is ProtocolNoticeMsgRptModel                     -> source.transformToDomainModel()
            is ProtocolMsgSolutionRspModel                   -> source.transformToDomainModel()
            is ProtocolPatchInitRspModel                     -> source.transformToDomainModel()
            is ProtocolPatchRecoveryRptModel                 -> source.transformToDomainModel()
            is ProtocolAppAuthKeyAckRspModel                 -> source.transformToDomainModel()
            is ProtocolAdditionalPrimingRspModel             -> source.transformToDomainModel()
            is ProtocolPatchAlertAlarmSetRspModel            -> source.transformToDomainModel()
            is ProtocolAppAuthAckRspModel                    -> source.transformToDomainModel()
            is ProtocolInfusionThresholdRspModel             -> source.transformToDomainModel()
            is ProtocolNoticeThresholdRspModel               -> source.transformToDomainModel()
            else                                             -> null
        }
    }
}