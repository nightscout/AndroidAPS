package app.aaps.pump.carelevo.data.dataSource.remote

import app.aaps.pump.carelevo.ble.data.CommandResult
import app.aaps.pump.carelevo.data.model.ble.BleResponse
import app.aaps.pump.carelevo.data.model.ble.ProtocolRspModel
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single

interface CarelevoBtBolusRemoteDataSource {

    fun getBolusResponse(): Observable<BleResponse<ProtocolRspModel>>

    fun manipulateStartImmeBolusInfusionProgram(actionId: Int, bolus: Double): Single<CommandResult<Boolean>>
    fun manipulateCancelImmeBolusInfusionProgram(): Single<CommandResult<Boolean>>

    fun manipulateStartExtendBolusInfusionProgram(immeDose: Double, extendSpeed: Double, hour: Int, min: Int): Single<CommandResult<Boolean>>
    fun manipulateCancelExtendBolusInfusionProgram(): Single<CommandResult<Boolean>>
}