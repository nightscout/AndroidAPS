package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting

import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultSuccess
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.model.CarelevoUserSettingInfoRequestModel
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject

class CarelevoCreateUserSettingInfoUseCase @Inject constructor(
    private val userSettingInfoRepository: CarelevoUserSettingInfoRepository
) {

    fun execute(request: CarelevoUseCaseRequest): Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
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
        }.observeOn(Schedulers.io())
    }
}