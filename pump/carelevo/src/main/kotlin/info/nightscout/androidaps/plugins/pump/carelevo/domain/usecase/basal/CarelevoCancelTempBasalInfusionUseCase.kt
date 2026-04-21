package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal

import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.CancelTempBasalProgramResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.Result
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultSuccess
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoBasalRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.ofType
import io.reactivex.rxjava3.schedulers.Schedulers
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoCancelTempBasalInfusionUseCase @Inject constructor(
    private val patchObserver : CarelevoPatchObserver,
    private val basalRepository : CarelevoBasalRepository,
    private val patchInfoRepository : CarelevoPatchInfoRepository,
    private val infusionInfoRepository : CarelevoInfusionInfoRepository
) {

    fun execute() : Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                basalRepository.requestCancelTempBasalProgram()
                    .blockingGet()
                    .takeIf { it is RequestResult.Pending }
                    ?: throw IllegalStateException("request cancel temp basal is not pending")

                val cancelTempBasalResult = patchObserver.basalEvent
                    .ofType<CancelTempBasalProgramResultModel>()
                    .blockingFirst()

                if(cancelTempBasalResult.result != Result.SUCCESS) {
                    throw IllegalStateException("request cancel temp basal result is failed")
                }

                val deleteTempBasalBasalInfusionInfoResult = infusionInfoRepository.deleteTempBasalInfusionInfo()
                if(!deleteTempBasalBasalInfusionInfoResult) {
                    throw IllegalStateException("delete temp basal infusion info is failed")
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