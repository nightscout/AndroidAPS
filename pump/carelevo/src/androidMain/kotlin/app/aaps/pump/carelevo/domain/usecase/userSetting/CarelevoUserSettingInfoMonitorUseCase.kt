package app.aaps.pump.carelevo.domain.usecase.userSetting

import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class CarelevoUserSettingInfoMonitorUseCase @Inject constructor(
    private val userSettingInfoRepository: CarelevoUserSettingInfoRepository
) {

    /** Every new value of the store, as a result. A store that cannot give its stream is one error. */
    fun execute(): Flow<ResponseResult<CarelevoUseCaseResponse>> =
        runCatching { userSettingInfoRepository.getUserSettingInfo() }.fold(
            onSuccess = { values -> values.map { ResponseResult.Success(it) } },
            onFailure = { error -> flowOf(ResponseResult.Error(error)) }
        )
}