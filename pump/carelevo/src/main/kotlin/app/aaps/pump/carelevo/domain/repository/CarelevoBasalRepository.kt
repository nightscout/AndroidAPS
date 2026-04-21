package app.aaps.pump.carelevo.domain.repository

import app.aaps.pump.carelevo.domain.model.RequestResult
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.bt.BtResponse
import app.aaps.pump.carelevo.domain.model.bt.SetBasalProgramAdditionalRequest
import app.aaps.pump.carelevo.domain.model.bt.SetBasalProgramRequest
import app.aaps.pump.carelevo.domain.model.bt.SetBasalProgramRequestV2
import app.aaps.pump.carelevo.domain.model.bt.StartTempBasalProgramByPercentRequest
import app.aaps.pump.carelevo.domain.model.bt.StartTempBasalProgramByUnitRequest
import app.aaps.pump.carelevo.domain.model.bt.UpdateBasalProgramAdditionalRequest
import app.aaps.pump.carelevo.domain.model.bt.UpdateBasalProgramRequest
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single

interface CarelevoBasalRepository {

    fun getResponseResult(): Observable<ResponseResult<BtResponse>>

    fun requestSetBasalProgram(param: SetBasalProgramRequest): Single<RequestResult<Boolean>>
    fun requestSetAdditionalBasalProgram(param: SetBasalProgramAdditionalRequest): Single<RequestResult<Boolean>>

    fun requestUpdateBasalProgram(param: UpdateBasalProgramRequest): Single<RequestResult<Boolean>>
    fun requestUpdateAdditionalBasalProgram(param: UpdateBasalProgramAdditionalRequest): Single<RequestResult<Boolean>>

    fun requestStartTempBasalProgramByUnit(param: StartTempBasalProgramByUnitRequest): Single<RequestResult<Boolean>>
    fun requestStartTempBasalProgramByPercent(param: StartTempBasalProgramByPercentRequest): Single<RequestResult<Boolean>>

    fun requestCancelTempBasalProgram(): Single<RequestResult<Boolean>>

    fun reserveCompleteTempBasal(userId: String, address: String, infusionId: String, expectedSeconds: Long): RequestResult<Boolean>

    fun requestSetBasalProgramV2(param: SetBasalProgramRequestV2): Single<RequestResult<Boolean>>
    fun requestUpdateBasalProgramV2(param: SetBasalProgramRequestV2): Single<RequestResult<Boolean>>
}