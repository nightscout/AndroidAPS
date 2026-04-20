package info.nightscout.androidaps.plugins.pump.carelevo.data.repository

import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.CommandResult
import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.remote.CarelevoBtBasalRemoteDataSource
import info.nightscout.androidaps.plugins.pump.carelevo.data.mapper.transformToDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.BleResponse
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolAdditionalBasalInfusionChangeRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolAdditionalBasalProgramSetRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolBasalInfusionChangeRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolBasalProgramSetRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolSegmentModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolTempBasalInfusionCancelRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolTempBasalInfusionRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.BtResponse
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SetBasalProgramAdditionalRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SetBasalProgramRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SetBasalProgramRequestV2
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.StartTempBasalProgramByPercentRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.StartTempBasalProgramByUnitRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.UpdateBasalProgramAdditionalRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.UpdateBasalProgramRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoBasalRepository
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import javax.inject.Inject

class CarelevoBasalRepositoryImpl @Inject constructor(
    private val btBasalRemoteDataSource : CarelevoBtBasalRemoteDataSource
) : CarelevoBasalRepository {

    override fun getResponseResult(): Observable<ResponseResult<BtResponse>> {
        return btBasalRemoteDataSource.getBasalResponse().map {
            when(it) {
                is BleResponse.RspResponse -> ResponseResult.Success(transformToDomainModel(it.data))
                is BleResponse.Error       -> ResponseResult.Error(it.e)
                is BleResponse.Failure     -> ResponseResult.Failure(it.message)
            }
        }
    }

    override fun requestSetBasalProgram(param: SetBasalProgramRequest): Single<RequestResult<Boolean>> {
        return btBasalRemoteDataSource.setBasalInfusionProgram(
            totalSegmentCnt = param.totalSegmentCnt,
            segments = param.segmentList.map {
                ProtocolSegmentModel(
                    injectHour = it.injectStartHour,
                    injectMin = it.injectStartMin,
                    injectSpeed = it.injectSpeed
                )
            }
        ).map {
            mapToResult(it)
        }
    }

    override fun requestSetAdditionalBasalProgram(param: SetBasalProgramAdditionalRequest): Single<RequestResult<Boolean>> {
        return btBasalRemoteDataSource.setAdditionalBasalInfusionProgram(
            msgNo = param.msgNumber,
            segmentCnt = param.segmentCnt,
            segments = param.segmentList.map {
                ProtocolSegmentModel(
                    injectHour = it.injectStartHour,
                    injectMin = it.injectStartMin,
                    injectSpeed = it.injectSpeed
                )
            }
        ).map {
            mapToResult(it)
        }
    }

    override fun requestUpdateBasalProgram(param: UpdateBasalProgramRequest): Single<RequestResult<Boolean>> {
        return btBasalRemoteDataSource.setUpdateBasalInfusionProgram(
            totalSegmentCnt = param.totalBasalSegmentCnt,
            segments = param.segmentList.map {
                ProtocolSegmentModel(
                    injectHour = it.injectStartHour,
                    injectMin = it.injectStartMin,
                    injectSpeed = it.injectSpeed
                )
            }
        ).map {
            mapToResult(it)
        }
    }

    override fun requestUpdateAdditionalBasalProgram(param: UpdateBasalProgramAdditionalRequest): Single<RequestResult<Boolean>> {
        return btBasalRemoteDataSource.setUpdateAdditionalBasalInfusionProgram(
            msgNo = param.msgNumber,
            segmentCnt = param.segmentCnt,
            segments = param.segmentList.map {
                ProtocolSegmentModel(
                    injectHour = it.injectStartHour,
                    injectMin = it.injectStartMin,
                    injectSpeed = it.injectSpeed
                )
            }
        ).map {
            mapToResult(it)
        }
    }

    override fun requestStartTempBasalProgramByUnit(param: StartTempBasalProgramByUnitRequest): Single<RequestResult<Boolean>> {
        return btBasalRemoteDataSource.manipulateStartTempBasalInfusionProgramByUnit(
            infusionUnit = param.infusionUnit,
            infusionHour = param.infusionHour,
            infusionMin = param.infusionMin
        ).map {
            mapToResult(it)
        }
    }

    override fun requestStartTempBasalProgramByPercent(param: StartTempBasalProgramByPercentRequest): Single<RequestResult<Boolean>> {
        return btBasalRemoteDataSource.manipulateStartTempBasalInfusionProgramByPercent(
            infusionPercent = param.infusionPercent,
            infusionHour = param.infusionHour,
            infusionMin = param.infusionMin
        ).map {
            mapToResult(it)
        }
    }

    override fun requestCancelTempBasalProgram(): Single<RequestResult<Boolean>> {
        return btBasalRemoteDataSource.manipulateCancelTempBasalInfusionProgram().map { mapToResult(it) }
    }

    override fun reserveCompleteTempBasal(userId: String, address: String, infusionId: String, expectedSeconds: Long): RequestResult<Boolean> {
        return RequestResult.Pending(true)
    }

    override fun requestSetBasalProgramV2(param: SetBasalProgramRequestV2): Single<RequestResult<Boolean>> {
        return btBasalRemoteDataSource.setBasalInfusionProgramV2(
            seqNo = param.seqNo,
            segments = param.segmentList.map {
                ProtocolSegmentModel(
                    injectHour = it.injectStartHour,
                    injectMin = it.injectStartMin,
                    injectSpeed = it.injectSpeed
                )
            }
        ).map { mapToResult(it) }
    }

    override fun requestUpdateBasalProgramV2(param: SetBasalProgramRequestV2): Single<RequestResult<Boolean>> {
        return btBasalRemoteDataSource.updateBasalInfusionProgramV2(
            seqNo = param.seqNo,
            segments = param.segmentList.map {
                ProtocolSegmentModel(
                    injectHour = it.injectStartHour,
                    injectMin = it.injectStartMin,
                    injectSpeed = it.injectSpeed
                )
            }
        ).map { mapToResult(it) }
    }

    private fun mapToResult(result : CommandResult<Boolean>) : RequestResult<Boolean> {
        return when(result) {
            is CommandResult.Pending -> RequestResult.Pending(result.data)
            is CommandResult.Success -> RequestResult.Success(result.data)
            is CommandResult.Error   -> RequestResult.Error(result.e)
            is CommandResult.Failure -> RequestResult.Failure(result.message)
        }
    }

    private fun transformToDomainModel(source : ProtocolRspModel) : BtResponse? {
        return when(source) {
            is ProtocolBasalProgramSetRspModel               -> source.transformToDomainModel()
            is ProtocolAdditionalBasalProgramSetRspModel     -> source.transformToDomainModel()
            is ProtocolBasalInfusionChangeRspModel           -> source.transformToDomainModel()
            is ProtocolAdditionalBasalInfusionChangeRspModel -> source.transformToDomainModel()
            is ProtocolTempBasalInfusionRspModel             -> source.transformToDomainModel()
            is ProtocolTempBasalInfusionCancelRspModel       -> source.transformToDomainModel()
            else                                             -> null
        }
    }
}