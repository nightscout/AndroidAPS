package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus

import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.CancelImmeBolusResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.Result
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoBolusRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.model.CancelBolusInfusionResponseModel
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.ofType
import io.reactivex.rxjava3.schedulers.Schedulers
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoCancelImmeBolusInfusionUseCase @Inject constructor(
    private val patchObserver : CarelevoPatchObserver,
    private val bolusRepository : CarelevoBolusRepository,
    private val patchInfoRepository : CarelevoPatchInfoRepository,
    private val infusionInfoRepository : CarelevoInfusionInfoRepository
) {

    fun execute() : Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                bolusRepository.requestCancelImmeBolus()
                    .blockingGet()
                    .takeIf { it is RequestResult.Pending }
                    ?: throw IllegalStateException("request cancel imme bolus is not pending")

                val cancelImmeBolusResult = patchObserver.bolusEvent
                    .ofType<CancelImmeBolusResultModel>()
                    .blockingFirst()

                if(cancelImmeBolusResult.result != Result.SUCCESS) {
                    throw IllegalStateException("request cancel imme bolus result is failed")
                }

                val deleteImmeBolusInfusionInfoResult = infusionInfoRepository.deleteImmeBolusInfusionInfo()
                if(!deleteImmeBolusInfusionInfoResult) {
                    throw IllegalStateException("delete imme bolus infusion info is failed")
                }

                val infusionInfo = infusionInfoRepository.getInfusionInfoBySync()
                    ?: throw NullPointerException("infusion info must be not null")

                val mode = if(infusionInfo.extendBolusInfusionInfo != null) {
                    5
                } else if(infusionInfo.immeBolusInfusionInfo != null) {
                    3
                } else if(infusionInfo.tempBasalInfusionInfo != null) {
                    2
                } else if(infusionInfo.basalInfusionInfo != null) {
                    if(infusionInfo.basalInfusionInfo.isStop) {
                        0
                    } else {
                        1
                    }
                } else {
                    throw NullPointerException("infusion info must be not null")
                }

                val patchInfo = patchInfoRepository.getPatchInfoBySync()
                    ?: throw NullPointerException("patch info must be not null")

                val updatePatchInfoResult = patchInfoRepository.updatePatchInfo(patchInfo.copy(updatedAt = DateTime.now(), mode = mode))
                if(!updatePatchInfoResult) {
                    throw IllegalStateException("update patch info is failed")
                }

                CancelBolusInfusionResponseModel(
                    infusedAmount = cancelImmeBolusResult.infusedAmount
                )
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