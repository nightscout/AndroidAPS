package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting

import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultSuccess
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject

class CarelevoDeleteUserSettingInfoUseCase @Inject constructor(
    private val userSettingInfoRepository : CarelevoUserSettingInfoRepository
) {

    fun execute() : Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                val deleteResult = userSettingInfoRepository.deleteUserSettingInfo()
                if(!deleteResult) {
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
        }.observeOn(Schedulers.io())
    }
}