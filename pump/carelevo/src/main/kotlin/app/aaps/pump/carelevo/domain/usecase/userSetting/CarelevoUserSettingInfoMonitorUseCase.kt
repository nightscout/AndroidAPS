package app.aaps.pump.carelevo.domain.usecase.userSetting

import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import io.reactivex.rxjava3.core.Observable
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

class CarelevoUserSettingInfoMonitorUseCase @Inject constructor(
    private val userSettingInfoRepository: CarelevoUserSettingInfoRepository
) {

    fun execute(): Observable<ResponseResult<CarelevoUseCaseResponse>> {
        return runCatching {
            userSettingInfoRepository.getUserSettingInfo()
        }.fold(
            onSuccess = { resultObservable ->
                resultObservable
                    .map { result ->
                        ResponseResult.Success(result.getOrNull())
                    }
            },
            onFailure = {
                Observable.just(ResponseResult.Error(it))
            }
        )
    }
}