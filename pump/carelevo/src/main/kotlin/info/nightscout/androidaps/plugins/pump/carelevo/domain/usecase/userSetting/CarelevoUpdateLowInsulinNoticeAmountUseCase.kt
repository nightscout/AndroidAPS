package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.PatchState
import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.Result
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SetThresholdNoticeRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SetThresholdNoticeResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultSuccess
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.model.CarelevoUserSettingInfoRequestModel
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.ofType
import io.reactivex.rxjava3.schedulers.Schedulers
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoUpdateLowInsulinNoticeAmountUseCase @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val patchObserver: CarelevoPatchObserver,
    private val patchRepository: CarelevoPatchRepository,
    private val userSettingInfoRepository: CarelevoUserSettingInfoRepository
) {

    fun execute(request: CarelevoUseCaseRequest): Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                if (request !is CarelevoUserSettingInfoRequestModel) {
                    throw IllegalArgumentException("request is not CarelevoUserSettingInfoRequestModel")
                }

                if (request.lowInsulinNoticeAmount == null || request.patchState == null) {
                    throw IllegalArgumentException("patch state, low insulin notice amount must be not null")
                }

                val userSettingInfo = userSettingInfoRepository.getUserSettingInfoBySync()
                    ?: throw NullPointerException("user setting info must be not null")

                when (request.patchState) {
                    is PatchState.ConnectedBooted -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "case1 connected requestPatchAndLocalUpdate")
                        patchRepository.requestSetThresholdNotice(SetThresholdNoticeRequest(request.lowInsulinNoticeAmount, 0))
                            .blockingGet()
                            .takeIf { it is RequestResult.Pending }
                            ?: throw IllegalStateException("request update low insulin notice amount is not pending")

                        val requestResult = patchObserver.patchEvent
                            .ofType<SetThresholdNoticeResultModel>()
                            .blockingFirst()

                        if (requestResult.result != Result.SUCCESS) {
                            throw IllegalStateException("request update low insulin notice amount result is failed")
                        }

                        val updateUserSettingInfoResult = userSettingInfoRepository.updateUserSettingInfo(
                            userSettingInfo.copy(updatedAt = DateTime.now(), lowInsulinNoticeAmount = request.lowInsulinNoticeAmount, needLowInsulinNoticeAmountSyncPatch = false)
                        )
                        if (!updateUserSettingInfoResult) {
                            throw IllegalStateException("update user setting info is failed")
                        }
                    }

                    is PatchState.NotConnectedNotBooting -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "case2 notConnected localUpdate syncPatch=false")
                        val updateUserSettingInfoResult = userSettingInfoRepository.updateUserSettingInfo(
                            userSettingInfo.copy(updatedAt = DateTime.now(), lowInsulinNoticeAmount = request.lowInsulinNoticeAmount, needLowInsulinNoticeAmountSyncPatch = false)
                        )
                        if (!updateUserSettingInfoResult) {
                            throw IllegalStateException("update user setting info is failed")
                        }
                    }

                    else -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "case3 disconnected localUpdate syncPatch=true")
                        val updateUserSettingInfoResult = userSettingInfoRepository.updateUserSettingInfo(
                            userSettingInfo.copy(updatedAt = DateTime.now(), lowInsulinNoticeAmount = request.lowInsulinNoticeAmount, needLowInsulinNoticeAmountSyncPatch = true)
                        )
                        if (!updateUserSettingInfoResult) {
                            throw IllegalStateException("update user setting info is failed")
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
