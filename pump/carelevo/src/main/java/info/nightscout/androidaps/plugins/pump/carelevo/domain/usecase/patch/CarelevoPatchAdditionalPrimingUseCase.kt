package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch

import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.AdditionalPrimingResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.Result
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultSuccess
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.ofType
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject

class CarelevoPatchAdditionalPrimingUseCase @Inject constructor(
    private val patchRepository: CarelevoPatchRepository,
    private val patchObserver: CarelevoPatchObserver
) {

    fun execute(): Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                patchRepository.requestAdditionalPriming()
                    .blockingGet()
                    .takeIf { it is RequestResult.Pending }
                    ?: throw IllegalStateException("Failed to request additional priming")

                val resultAdditionalPriming = patchObserver.patchEvent
                    .ofType<AdditionalPrimingResultModel>()
                    .blockingFirst()

                if (resultAdditionalPriming.result == Result.SUCCESS) {
                    ResultSuccess
                } else {
                    throw IllegalStateException("Failed to request additional priming")
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
