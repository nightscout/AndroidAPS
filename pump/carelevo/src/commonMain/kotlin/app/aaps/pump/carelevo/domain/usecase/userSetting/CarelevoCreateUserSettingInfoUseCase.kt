package app.aaps.pump.carelevo.domain.usecase.userSetting

import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.result.ResultSuccess
import app.aaps.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import app.aaps.pump.carelevo.domain.usecase.userSetting.model.CarelevoUserSettingInfoRequestModel
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.withContext

class CarelevoCreateUserSettingInfoUseCase @Inject constructor(
    private val userSettingInfoRepository: CarelevoUserSettingInfoRepository
) {

    suspend fun execute(request: CarelevoUseCaseRequest): ResponseResult<CarelevoUseCaseResponse> = withContext(aapsIoDispatcher) {
        runCatching {
            if (request !is CarelevoUserSettingInfoRequestModel) {
                throw IllegalArgumentException("request is not CarelevoUserSettingInfoRequestModel")
            }

            val createResult = userSettingInfoRepository.updateUserSettingInfo(
                CarelevoUserSettingInfoDomainModel(
                    lowInsulinNoticeAmount = request.lowInsulinNoticeAmount,
                    maxBasalSpeed = request.maxBasalSpeed,
                    maxBolusDose = request.maxBolusDose
                )
            )

            if (!createResult) {
                throw IllegalStateException("create user setting info is failed")
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