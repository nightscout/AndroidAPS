package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch

import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import io.reactivex.rxjava3.core.Observable
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

class CarelevoPatchInfoMonitorUseCase @Inject constructor(
    private val patchInfoRepository : CarelevoPatchInfoRepository
) {

    fun execute() : Observable<ResponseResult<CarelevoUseCaseResponse>> {
        return runCatching {
            patchInfoRepository.getPatchInfo()
        }.fold(
            onSuccess = { resultObservable ->
                resultObservable
                    .map { result ->
                        ResponseResult.Success(result.getOrNull())
                    }
            },
            onFailure = { error ->
                Observable.just(ResponseResult.Error(error))
            }
        )
    }
}