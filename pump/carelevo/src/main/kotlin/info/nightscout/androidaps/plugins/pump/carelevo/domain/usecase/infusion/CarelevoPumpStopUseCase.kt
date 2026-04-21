package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.infusion

import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.Result
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.StopPumpRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.StopPumpResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultSuccess
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.infusion.model.CarelevoPumpStopRequestModel
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.ofType
import io.reactivex.rxjava3.schedulers.Schedulers
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoPumpStopUseCase @Inject constructor(
    private val patchObserver: CarelevoPatchObserver,
    private val patchRepository: CarelevoPatchRepository,
    private val patchInfoRepository: CarelevoPatchInfoRepository,
    private val infusionInfoRepository: CarelevoInfusionInfoRepository
) {

    fun execute(request: CarelevoUseCaseRequest): Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                if (request !is CarelevoPumpStopRequestModel) {
                    throw IllegalArgumentException("request is not CarelevoPumpStopRequestModel")
                }

                patchRepository.requestStopPump(StopPumpRequest(request.durationMin, 0))
                    .blockingGet()
                    .takeIf { it is RequestResult.Pending }
                    ?: throw IllegalStateException("request stop pump is not pending")

                val requestStopPumpResult = patchObserver.patchEvent
                    .ofType<StopPumpResultModel>()
                    .blockingFirst()

                if (requestStopPumpResult.result != Result.SUCCESS) {
                    throw IllegalStateException("request stop pump result is failed")
                }

                val infusionInfo = infusionInfoRepository.getInfusionInfoBySync()
                    ?: throw NullPointerException("infusion info must be not null")
                val basalInfusionInfo = infusionInfo.basalInfusionInfo
                    ?: throw NullPointerException("basal infusion info must be not null")

                val updateInfusionInfoResult = infusionInfoRepository.updateBasalInfusionInfo(
                    basalInfusionInfo.copy(updatedAt = DateTime.now(), mode = 0, isStop = true)
                )
                if (!updateInfusionInfoResult) {
                    throw IllegalStateException("update infusion info is failed")
                }

                val patchInfo = patchInfoRepository.getPatchInfoBySync()
                    ?: throw NullPointerException("patch info must be not null")

                val updatePatchInfoResult = patchInfoRepository.updatePatchInfo(
                    patchInfo.copy(updatedAt = DateTime.now(), isStopped = true, stopMinutes = request.durationMin, stopMode = 0, isForceStopped = false, mode = 0)
                )

                if (!updatePatchInfoResult) {
                    throw IllegalStateException("update patch info is failed")
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