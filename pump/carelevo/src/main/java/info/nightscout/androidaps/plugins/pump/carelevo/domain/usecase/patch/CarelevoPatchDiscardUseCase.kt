package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch

import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.DiscardPatchResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.Result
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultSuccess
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.ofType
import io.reactivex.rxjava3.schedulers.Schedulers
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoPatchDiscardUseCase @Inject constructor(
    private val patchObserver : CarelevoPatchObserver,
    private val patchRepository : CarelevoPatchRepository,
    private val patchInfoRepository : CarelevoPatchInfoRepository,
    private val infusionInfoRepository : CarelevoInfusionInfoRepository,
    private val userSettingInfoRepository : CarelevoUserSettingInfoRepository
) {

    fun execute() : Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                patchRepository.requestDiscardPatch()
                    .blockingGet()
                    .takeIf { it is RequestResult.Pending }
                    ?: throw IllegalStateException("request patch discard is not pending")

                val requestDiscardResult = patchObserver.patchEvent
                    .ofType<DiscardPatchResultModel>()
                    .blockingFirst()

                if(requestDiscardResult.result != Result.SUCCESS) {
                    throw IllegalStateException("request patch discard result is failed")
                }

                val userSettingInfo = userSettingInfoRepository.getUserSettingInfoBySync()
                    ?: throw NullPointerException("user setting info must be not null")

                val updateUserSettingInfoResult = userSettingInfoRepository.updateUserSettingInfo(
                    userSettingInfo.copy(updatedAt = DateTime.now(), needMaxBolusDoseSyncPatch = false, needMaxBasalSpeedSyncPatch = false, needLowInsulinNoticeAmountSyncPatch = false)
                )
                if(!updateUserSettingInfoResult) {
                    throw IllegalStateException("update user setting info is failed")
                }

                val deleteInfusionInfoResult = infusionInfoRepository.deleteInfusionInfo()
                if(!deleteInfusionInfoResult) {
                    throw IllegalStateException("delete infusion info is failed")
                }

                val deletePatchInfoResult = patchInfoRepository.deletePatchInfo()
                if(!deletePatchInfoResult) {
                    throw IllegalStateException("delete patch info is failed")
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
        }.subscribeOn(Schedulers.io())
    }
}