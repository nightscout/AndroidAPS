package app.aaps.pump.carelevo.domain.usecase.infusion

import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class CarelevoInfusionInfoMonitorUseCase @Inject constructor(
    private val infusionInfoRepository: CarelevoInfusionInfoRepository
) {

    /** Every new value of the store, as a result. A store that cannot give its stream is one error. */
    fun execute(): Flow<ResponseResult<CarelevoUseCaseResponse>> =
        runCatching { infusionInfoRepository.getInfusionInfo() }.fold(
            onSuccess = { values -> values.map { ResponseResult.Success(it) } },
            onFailure = { error -> flowOf(ResponseResult.Error(error)) }
        )
}