package app.aaps.pump.carelevo.domain.usecase.userSetting

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.domain.CarelevoPatchObserver
import app.aaps.pump.carelevo.domain.model.RequestResult
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.bt.Result
import app.aaps.pump.carelevo.domain.model.bt.SetInfusionThresholdResultModel
import app.aaps.pump.carelevo.domain.model.bt.SetThresholdInfusionMaxDoseRequest
import app.aaps.pump.carelevo.domain.model.result.ResultSuccess
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import app.aaps.pump.carelevo.domain.usecase.userSetting.model.CarelevoUserSettingInfoRequestModel
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.ofType
import io.reactivex.rxjava3.schedulers.Schedulers
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoUpdateMaxBolusDoseUseCase @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val patchObserver: CarelevoPatchObserver,
    private val patchRepository: CarelevoPatchRepository,
    private val infusionInfoRepository: CarelevoInfusionInfoRepository,
    private val userSettingInfoRepository: CarelevoUserSettingInfoRepository
) {

    fun execute(request: CarelevoUseCaseRequest): Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                if (request !is CarelevoUserSettingInfoRequestModel) {
                    throw IllegalArgumentException("request is not CarelevoUserSettingInfoRequestModel")
                }
                if (request.maxBolusDose == null || request.patchState == null) {
                    throw IllegalArgumentException("max bolus dose, patch state must be not null")
                }

                val infusionInfo = infusionInfoRepository.getInfusionInfoBySync()

                val userSettingInfo = userSettingInfoRepository.getUserSettingInfoBySync()
                    ?: throw NullPointerException("user setting info must be not null")

                if (infusionInfo?.immeBolusInfusionInfo != null || infusionInfo?.extendBolusInfusionInfo != null) {
                    aapsLogger.debug(LTag.PUMPCOMM, "case1 bolusRunning localUpdate syncPatch=true")

                    val updateUserSettingInfoResult = userSettingInfoRepository.updateUserSettingInfo(
                        userSettingInfo.copy(updatedAt = DateTime.now(), maxBolusDose = request.maxBolusDose, needMaxBolusDoseSyncPatch = true)
                    )
                    if (!updateUserSettingInfoResult) {
                        throw IllegalStateException("update user setting info is failed")
                    }
                } else {
                    when (request.patchState) {
                        is PatchState.ConnectedBooted -> {
                            aapsLogger.debug(LTag.PUMPCOMM, "case2 connected requestPatchAndLocalUpdate")
                            patchRepository.requestSetThresholdMaxDose(SetThresholdInfusionMaxDoseRequest(request.maxBolusDose))
                                .blockingGet()
                                .takeIf { it is RequestResult.Pending }
                                ?: throw IllegalStateException("request update max bolus dose is not pending")

                            val requestUpdateMaxBolusDoseResult = patchObserver.patchEvent
                                .ofType<SetInfusionThresholdResultModel>()
                                .blockingFirst()

                            if (requestUpdateMaxBolusDoseResult.result != Result.SUCCESS) {
                                throw IllegalStateException("request update max bolus dose result is failed")
                            }

                            val updateUserSettingInfoResult = userSettingInfoRepository.updateUserSettingInfo(
                                userSettingInfo.copy(updatedAt = DateTime.now(), maxBolusDose = request.maxBolusDose, needMaxBolusDoseSyncPatch = false)
                            )
                            if (!updateUserSettingInfoResult) {
                                throw IllegalStateException("update user setting info is failed")
                            }
                        }

                        is PatchState.NotConnectedNotBooting -> {
                            aapsLogger.debug(LTag.PUMPCOMM, "case3 notConnected localUpdate syncPatch=false")
                            val updateUserSettingInfoResult = userSettingInfoRepository.updateUserSettingInfo(
                                userSettingInfo.copy(updatedAt = DateTime.now(), maxBolusDose = request.maxBolusDose, needMaxBolusDoseSyncPatch = false)
                            )
                            if (!updateUserSettingInfoResult) {
                                throw IllegalStateException("update user setting info is failed")
                            }
                        }

                        else -> {
                            aapsLogger.debug(LTag.PUMPCOMM, "case4 disconnected localUpdate syncPatch=true")
                            val updateUserSettingInfoResult = userSettingInfoRepository.updateUserSettingInfo(
                                userSettingInfo.copy(updatedAt = DateTime.now(), maxBolusDose = request.maxBolusDose, needMaxBolusDoseSyncPatch = true)
                            )
                            if (!updateUserSettingInfoResult) {
                                throw IllegalStateException("update user setting info is failed")
                            }
                        }
                    }
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
