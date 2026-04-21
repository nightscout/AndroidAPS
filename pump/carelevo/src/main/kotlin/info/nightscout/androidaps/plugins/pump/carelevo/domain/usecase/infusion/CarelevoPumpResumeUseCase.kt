package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.infusion

import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.ResumePumpRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.ResumePumpResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.StopPumpResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultSuccess
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.ofType
import io.reactivex.rxjava3.schedulers.Schedulers
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoPumpResumeUseCase @Inject constructor(
    private val patchObserver: CarelevoPatchObserver,
    private val patchRepository: CarelevoPatchRepository,
    private val patchInfoRepository: CarelevoPatchInfoRepository,
    private val infusionInfoRepository: CarelevoInfusionInfoRepository
) {

    fun execute(): Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                patchRepository.requestResumePump(ResumePumpRequest(mode = 1, causeId = 0))
                    .blockingGet()
                    .takeIf { it is RequestResult.Pending }
                    ?: throw IllegalStateException("request pump resume is not pending")

                val requestResumePumpResult = patchObserver.patchEvent
                    .ofType<ResumePumpResultModel>()
                    .blockingFirst()

                if (requestResumePumpResult.result != StopPumpResult.BY_REQ) {
                    throw IllegalStateException("request pump resume result is failed: ${requestResumePumpResult.result}")
                }

                val infusionInfo = infusionInfoRepository.getInfusionInfoBySync()
                    ?: throw NullPointerException("infusion info must be not null")
                val basalInfusionInfo = infusionInfo.basalInfusionInfo
                    ?: throw NullPointerException("basal infusion info must be not null")

                val updateInfusionInfoResult = infusionInfoRepository.updateBasalInfusionInfo(
                    basalInfusionInfo.copy(updatedAt = DateTime.now(), mode = 1, isStop = false)
                )

                if (!updateInfusionInfoResult) {
                    throw IllegalStateException("update infusion info is failed")
                }

                val patchInfo = patchInfoRepository.getPatchInfoBySync()
                    ?: throw NullPointerException("patch info must be not null")

                val updatePatchInfoResult = patchInfoRepository.updatePatchInfo(
                    patchInfo.copy(isStopped = false, stopMinutes = null, stopMode = null, isForceStopped = null, mode = 1)
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