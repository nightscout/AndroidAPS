package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.infusion

import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

class CarelevoInfusionInfoMonitorUseCase @Inject constructor(
    private val infusionInfoRepository : CarelevoInfusionInfoRepository
) {

    fun execute() : Observable<ResponseResult<CarelevoUseCaseResponse>> {
        return runCatching {
            infusionInfoRepository.getInfusionInfo()
        }.fold(
            onSuccess = { resultObservable ->
                resultObservable
                    .observeOn(Schedulers.io())
                    .map { result ->
                        ResponseResult.Success(result.getOrNull())
                    }
            },
            onFailure = { error ->
                error.printStackTrace()
                Observable.just(ResponseResult.Error(error))
            }
        )
    }
}