package app.aaps.pump.carelevo.domain.usecase.userSetting

import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.result.ResultSuccess
import app.aaps.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.withContext

class CarelevoDeleteUserSettingInfoUseCase @Inject constructor(
    private val userSettingInfoRepository: CarelevoUserSettingInfoRepository
) {

    suspend fun execute(): ResponseResult<CarelevoUseCaseResponse> = withContext(aapsIoDispatcher) {
        runCatching {
            val deleteResult = userSettingInfoRepository.deleteUserSettingInfo()
            if (!deleteResult) {
                throw IllegalStateException("delete user setting info is failed")
            }
            ResultSuccess
        }.fold(
            onSuccess = {
                ResponseResult.Success(it as CarelevoUseCaseResponse)
            },
            onFailure = {
                ResponseResult.Error(it)
            }
        )
    }
}