package app.aaps.pump.carelevo.domain.usecase.infusion

import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.result.ResultSuccess
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import app.aaps.pump.carelevo.domain.usecase.infusion.model.CarelevoDeleteInfusionRequestModel
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoDeleteInfusionInfoUseCase @Inject constructor(
    private val patchInfoRepository: CarelevoPatchInfoRepository,
    private val infusionInfoRepository: CarelevoInfusionInfoRepository
) {

    fun execute(request: CarelevoUseCaseRequest): Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                require(request is CarelevoDeleteInfusionRequestModel) {
                    "Request must be CarelevoDeleteInfusionRequestModel"
                }
                val req = request

                if (req.isDeleteTempBasal) {
                    val ok = infusionInfoRepository.deleteTempBasalInfusionInfo()
                    if (!ok) error("Failed to delete temp basal infusion info")
                }
                if (req.isDeleteImmeBolus) {
                    val ok = infusionInfoRepository.deleteImmeBolusInfusionInfo()
                    if (!ok) error("Failed to delete immediate bolus infusion info")
                }
                if (req.isDeleteExtendBolus) {
                    val ok = infusionInfoRepository.deleteExtendBolusInfusionInfo()
                    if (!ok) error("Failed to delete extended bolus infusion info")
                }

                val infusionInfo = infusionInfoRepository.getInfusionInfoBySync()
                    ?: error("Infusion info must not be null after deletion step")

                val mode = when {
                    infusionInfo.extendBolusInfusionInfo != null -> 5
                    infusionInfo.immeBolusInfusionInfo != null   -> 3
                    infusionInfo.tempBasalInfusionInfo != null   -> 2

                    infusionInfo.basalInfusionInfo != null       -> {
                        if (infusionInfo.basalInfusionInfo.isStop) 0 else 1
                    }

                    else                                         -> 0
                }

                val now = DateTime.now()
                val patchInfo = patchInfoRepository.getPatchInfoBySync()
                    ?: error("Patch info must not be null")

                val updated = patchInfoRepository.updatePatchInfo(
                    patchInfo.copy(updatedAt = now, mode = mode)
                )
                if (!updated) error("Failed to update patch info (mode=$mode)")

                ResultSuccess
            }.fold(
                onSuccess = { ResponseResult.Success(it as CarelevoUseCaseResponse) },
                onFailure = { ResponseResult.Error(it) }
            )
        }.observeOn(Schedulers.io())
    }
}
