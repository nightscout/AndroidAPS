package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus

import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.ext.generateUUID
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SetBolusProgramResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.StartImmeBolusRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.StartImmeBolusResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoImmeBolusInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoBolusRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.model.StartImmeBolusInfusionRequestModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.model.StartImmeBolusInfusionResponseModel
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.ofType
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoStartImmeBolusInfusionUseCase @Inject constructor(
    private val patchObserver: CarelevoPatchObserver,
    private val bolusRepository: CarelevoBolusRepository,
    private val patchInfoRepository: CarelevoPatchInfoRepository,
    private val infusionInfoRepository: CarelevoInfusionInfoRepository
) {

    fun execute(request: CarelevoUseCaseRequest): Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                if (request !is StartImmeBolusInfusionRequestModel) {
                    throw IllegalArgumentException("request is not StartImmeBolusInfusionRequest")
                }

                bolusRepository.requestStartImmeBolus(StartImmeBolusRequest(actionId = request.actionSeq, volume = request.volume))
                    .blockingGet()
                    .takeIf { it is RequestResult.Pending }
                    ?: throw IllegalStateException("request start imme bolus is not pending")

                val startImmeBolusResult = patchObserver.bolusEvent
                    .ofType<StartImmeBolusResultModel>()
                    .blockingFirst()

                if (startImmeBolusResult.result != SetBolusProgramResult.SUCCESS) {
                    throw IllegalStateException("request start imme bolus result is failed")
                }

                val patchInfo = patchInfoRepository.getPatchInfoBySync()
                    ?: throw NullPointerException("patch info must be not null")

                val updateInfusionInfoResult = infusionInfoRepository.updateImmeBolusInfusionInfo(
                    CarelevoImmeBolusInfusionInfoDomainModel(
                        infusionId = generateUUID(),
                        address = patchInfo.address,
                        mode = 3,
                        volume = request.volume,
                        infusionDurationSeconds = startImmeBolusResult.expectedTime
                    )
                )

                if (!updateInfusionInfoResult) {
                    throw IllegalStateException("update infusion info is failed")
                }

                val updatePatchInfoResult = patchInfoRepository.updatePatchInfo(patchInfo.copy(updatedAt = DateTime.now(), mode = 3, bolusActionSeq = request.actionSeq))
                if (!updatePatchInfoResult) {
                    throw IllegalStateException("update patch info is failed")
                }

                StartImmeBolusInfusionResponseModel(
                    expectSec = startImmeBolusResult.expectedTime
                )
            }.fold(
                onSuccess = {
                    ResponseResult.Success(it as CarelevoUseCaseResponse)
                },
                onFailure = {
                    ResponseResult.Error(it)
                }
            )
        }
    }
}