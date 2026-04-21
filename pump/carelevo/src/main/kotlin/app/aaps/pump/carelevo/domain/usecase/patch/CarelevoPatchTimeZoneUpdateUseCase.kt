package app.aaps.pump.carelevo.domain.usecase.patch

import app.aaps.pump.carelevo.domain.CarelevoPatchObserver
import app.aaps.pump.carelevo.domain.model.RequestResult
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.bt.Result
import app.aaps.pump.carelevo.domain.model.bt.SetTimeRequest
import app.aaps.pump.carelevo.domain.model.bt.SetTimeResultModel
import app.aaps.pump.carelevo.domain.model.result.ResultSuccess
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchRepository
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import app.aaps.pump.carelevo.domain.usecase.patch.model.CarelevoPatchTimeZoneRequestModel
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

class CarelevoPatchTimeZoneUpdateUseCase(
    private val patchRepository: CarelevoPatchRepository,
    private val patchObserver: CarelevoPatchObserver
) {

    fun execute(request: CarelevoUseCaseRequest): Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                if (request !is CarelevoPatchTimeZoneRequestModel) {
                    throw IllegalArgumentException("request is not CarelevoPatchTimeZoneRequestModel")
                }

                patchRepository.requestSetTime(SetTimeRequest(DateTime.now(DateTimeZone.getDefault()).toString("yyyyMMddHHmm"), request.insulinAmount, 1, 0))
                    .blockingGet()
                    .takeIf { it is RequestResult.Pending }
                    ?: throw IllegalStateException("request set time is not pending")

                val patchInfoResult = patchObserver.patchEvent
                    .ofType(SetTimeResultModel::class.java)
                    .blockingFirst()

                if (patchInfoResult.result == Result.SUCCESS) {
                    ResultSuccess
                } else {
                    // retry
                    throw IllegalStateException("request set time is failed")
                }
            }.fold(
                onSuccess = {
                    ResponseResult.Success(it as CarelevoUseCaseResponse)
                },
                onFailure = {
                    ResponseResult.Error(it)
                }
            )
        }.observeOn(Schedulers.io())

    }
}