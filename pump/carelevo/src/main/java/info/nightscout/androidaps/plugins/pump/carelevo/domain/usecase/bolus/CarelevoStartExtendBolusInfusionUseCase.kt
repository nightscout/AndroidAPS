package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus

import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.ext.generateUUID
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SetBolusProgramResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.StartExtendBolusRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.StartExtendBolusResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoExtendBolusInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultSuccess
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoBolusRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.model.StartExtendBolusInfusionRequestModel
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.ofType
import io.reactivex.rxjava3.schedulers.Schedulers
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoStartExtendBolusInfusionUseCase @Inject constructor(
    private val patchObserver: CarelevoPatchObserver,
    private val bolusRepository: CarelevoBolusRepository,
    private val patchInfoRepository: CarelevoPatchInfoRepository,
    private val infusionInfoRepository: CarelevoInfusionInfoRepository
) {

    fun execute(request: CarelevoUseCaseRequest): Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                if (request !is StartExtendBolusInfusionRequestModel) {
                    throw IllegalArgumentException("request is not StartExtendBolusInfusionRequestModel")
                }

                val hour = request.minutes / 60
                val min = request.minutes % 60
                val duration = request.minutes.toDouble() / 60
                val speed = request.volume / duration

                bolusRepository.requestStartExtendBolus(StartExtendBolusRequest(volume = 0.0, speed = speed, hour = hour, min = min))
                    .blockingGet()
                    .takeIf { it is RequestResult.Pending }
                    ?: throw IllegalStateException("request start extend bolus is not pending")

                val startExtendBolusResult = patchObserver.bolusEvent
                    .ofType<StartExtendBolusResultModel>()
                    .blockingFirst()

                if (startExtendBolusResult.result != SetBolusProgramResult.SUCCESS) {
                    throw IllegalStateException("request start extend bolus result is failed")
                }

                val patchInfo = patchInfoRepository.getPatchInfoBySync()
                    ?: throw NullPointerException("patch info must be not null")

                val updateInfusionInfoResult = infusionInfoRepository.updateExtendBolusInfusionInfo(
                    CarelevoExtendBolusInfusionInfoDomainModel(
                        infusionId = generateUUID(),
                        address = patchInfo.address,
                        mode = 5,
                        volume = request.volume,
                        speed = speed,
                        infusionDurationMin = request.minutes
                    )
                )

                if (!updateInfusionInfoResult) {
                    throw IllegalStateException("update infusion info is failed")
                }

                val updatePatchInfoResult = patchInfoRepository.updatePatchInfo(patchInfo.copy(updatedAt = DateTime.now(), mode = 5))
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