package app.aaps.pump.carelevo.domain.usecase.patch

import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.result.ResultSuccess
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import app.aaps.pump.carelevo.domain.usecase.patch.model.CarelevoPatchRptInfusionInfoDefaultRequestModel
import app.aaps.pump.carelevo.domain.usecase.patch.model.CarelevoPatchRptInfusionInfoRequestModel
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoPatchRptInfusionInfoProcessUseCase @Inject constructor(
    private val patchInfoRepository: CarelevoPatchInfoRepository
) {

    fun execute(request: CarelevoUseCaseRequest): Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                if (request !is CarelevoPatchRptInfusionInfoRequestModel && request !is CarelevoPatchRptInfusionInfoDefaultRequestModel) {
                    throw IllegalArgumentException("request is not carelevoPatchRptInfusionInfoRequestModel")
                }

                val patchInfo = patchInfoRepository.getPatchInfoBySync()
                    ?: throw NullPointerException("patch info must be not null")

                val updatePatchInfoResult = patchInfoRepository.updatePatchInfo(
                    if (request is CarelevoPatchRptInfusionInfoRequestModel) {
                        patchInfo.copy(
                            mode = request.mode,
                            runningMinutes = request.runningMinute,
                            insulinRemain = request.remains,
                            infusedTotalBasalAmount = request.infusedTotalBasalAmount,
                            infusedTotalBolusAmount = request.infusedTotalBolusAmount,
                            pumpState = request.pumpState,
                            updatedAt = DateTime.now()
                        )
                    } else if (request is CarelevoPatchRptInfusionInfoDefaultRequestModel) {
                        patchInfo.copy(
                            insulinRemain = request.remains,
                        )
                    } else {
                        patchInfo
                    }
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
