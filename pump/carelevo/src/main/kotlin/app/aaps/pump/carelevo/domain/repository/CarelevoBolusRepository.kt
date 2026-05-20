package app.aaps.pump.carelevo.domain.repository

import app.aaps.pump.carelevo.domain.model.RequestResult
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.bt.BtResponse
import app.aaps.pump.carelevo.domain.model.bt.StartExtendBolusRequest
import app.aaps.pump.carelevo.domain.model.bt.StartImmeBolusRequest
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single

interface CarelevoBolusRepository {

    fun getResponseResult(): Observable<ResponseResult<BtResponse>>

    fun requestStartImmeBolus(param: StartImmeBolusRequest): Single<RequestResult<Boolean>>
    fun requestCancelImmeBolus(): Single<RequestResult<Boolean>>

    fun reserveCompleteImmeBolus(userId: String, address: String, infusionId: String, expectedSeconds: Long): RequestResult<Boolean>
    fun reserveCompleteExtendImmBolus(userId: String, address: String, infusionId: String, expectedSeconds: Long): RequestResult<Boolean>
    fun reserveCompleteExtendBolus(userId: String, address: String, infusionId: String, expectedSeconds: Long): RequestResult<Boolean>

    fun requestStartExtendBolus(param: StartExtendBolusRequest): Single<RequestResult<Boolean>>
    fun requestCancelExtendBolus(): Single<RequestResult<Boolean>>
}