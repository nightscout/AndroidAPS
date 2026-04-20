package info.nightscout.androidaps.plugins.pump.carelevo.data.repository

import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.CommandResult
import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.remote.CarelevoBtBolusRemoteDataSource
import info.nightscout.androidaps.plugins.pump.carelevo.data.mapper.transformToDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.BleResponse
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolBolusInfusionCancelRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolExtendBolusDelayRptModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolExtendBolusInfusionCancelRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolExtendBolusInfusionRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolImmeBolusInfusionRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.BtResponse
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.StartExtendBolusRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.StartImmeBolusRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoBolusRepository
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import javax.inject.Inject

class CarelevoBolusRepositoryImpl @Inject constructor(
    private val btBolusRemoteDataSource : CarelevoBtBolusRemoteDataSource
) : CarelevoBolusRepository {

    override fun getResponseResult(): Observable<ResponseResult<BtResponse>> {
        return btBolusRemoteDataSource.getBolusResponse().map {
            when(it) {
                is BleResponse.RspResponse -> ResponseResult.Success(transformToDomainModel(it.data))
                is BleResponse.Error       -> ResponseResult.Error(it.e)
                is BleResponse.Failure     -> ResponseResult.Failure(it.message)
            }
        }
    }

    override fun requestStartImmeBolus(param: StartImmeBolusRequest): Single<RequestResult<Boolean>> {
        return btBolusRemoteDataSource.manipulateStartImmeBolusInfusionProgram(
            actionId = param.actionId,
            bolus = param.volume
        ).map {
            mapToResult(it)
        }
    }

    override fun requestCancelImmeBolus(): Single<RequestResult<Boolean>> {
        return btBolusRemoteDataSource.manipulateCancelImmeBolusInfusionProgram().map { mapToResult(it) }
    }

    override fun reserveCompleteImmeBolus(userId: String, address: String, infusionId: String, expectedSeconds: Long): RequestResult<Boolean> {
        return RequestResult.Pending(true)
    }

    override fun reserveCompleteExtendImmBolus(userId: String, address: String, infusionId: String, expectedSeconds: Long): RequestResult<Boolean> {
        return RequestResult.Pending(true)
    }

    override fun reserveCompleteExtendBolus(userId: String, address: String, infusionId: String, expectedSeconds: Long): RequestResult<Boolean> {
        return RequestResult.Pending(true)
    }

    override fun requestStartExtendBolus(param: StartExtendBolusRequest): Single<RequestResult<Boolean>> {
        return btBolusRemoteDataSource.manipulateStartExtendBolusInfusionProgram(
            immeDose = param.volume,
            extendSpeed = param.speed,
            hour = param.hour,
            min = param.min
        ).map {
            mapToResult(it)
        }
    }

    override fun requestCancelExtendBolus(): Single<RequestResult<Boolean>> {
        return btBolusRemoteDataSource.manipulateCancelExtendBolusInfusionProgram().map { mapToResult(it) }
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
            is ProtocolImmeBolusInfusionRspModel         -> source.transformToDomainModel()
            is ProtocolBolusInfusionCancelRspModel       -> source.transformToDomainModel()
            is ProtocolExtendBolusInfusionRspModel       -> source.transformToDomainModel()
            is ProtocolExtendBolusInfusionCancelRspModel -> source.transformToDomainModel()
            is ProtocolExtendBolusDelayRptModel          -> source.transformToDomainModel()
            else                                         -> null
        }
    }
}