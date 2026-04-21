package app.aaps.pump.carelevo.domain.usecase.basal

import app.aaps.pump.carelevo.domain.CarelevoPatchObserver
import app.aaps.pump.carelevo.domain.ext.generateUUID
import app.aaps.pump.carelevo.domain.model.RequestResult
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.bt.SetBasalProgramResult
import app.aaps.pump.carelevo.domain.model.bt.StartTempBasalProgramByPercentRequest
import app.aaps.pump.carelevo.domain.model.bt.StartTempBasalProgramByUnitRequest
import app.aaps.pump.carelevo.domain.model.bt.StartTempBasalProgramResultModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoTempBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.result.ResultSuccess
import app.aaps.pump.carelevo.domain.repository.CarelevoBasalRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import app.aaps.pump.carelevo.domain.usecase.basal.model.StartTempBasalInfusionRequestModel
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.ofType
import io.reactivex.rxjava3.schedulers.Schedulers
import org.joda.time.DateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class CarelevoStartTempBasalInfusionUseCase @Inject constructor(
    private val patchObserver: CarelevoPatchObserver,
    private val basalRepository: CarelevoBasalRepository,
    private val patchInfoRepository: CarelevoPatchInfoRepository,
    private val infusionInfoRepository: CarelevoInfusionInfoRepository
) {

    fun execute(request: CarelevoUseCaseRequest): Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                if (request !is StartTempBasalInfusionRequestModel) {
                    throw IllegalArgumentException("request is not StartTempBasalInfusionRequestModel")
                }

                val patchInfo = patchInfoRepository.getPatchInfoBySync()
                    ?: throw NullPointerException("patch info must be not null")
                val hour = request.minutes / 60
                val min = request.minutes % 60

                val pendingResult = if (request.isUnit) {
                    if (request.speed == null) {
                        throw IllegalArgumentException("temp basal infusion type is unit, therefore speed must be not null")
                    }
                    basalRepository.requestStartTempBasalProgramByUnit(
                        StartTempBasalProgramByUnitRequest(
                            infusionUnit = request.speed,
                            infusionHour = hour,
                            infusionMin = min
                        )
                    )
                } else {
                    if (request.percent == null) {
                        throw IllegalArgumentException("temp basal infusion type is percent, therefore percent must be not null")
                    }
                    basalRepository.requestStartTempBasalProgramByPercent(
                        StartTempBasalProgramByPercentRequest(
                            infusionPercent = request.percent,
                            infusionHour = hour,
                            infusionMin = min
                        )
                    )
                }

                pendingResult
                    .blockingGet()
                    .takeIf { it is RequestResult.Pending }
                    ?: throw IllegalStateException("request start temp basal is not pending")

                val startTempBasalResult = patchObserver.basalEvent
                    .ofType<StartTempBasalProgramResultModel>()
                    .blockingFirst()

                if (startTempBasalResult.result != SetBasalProgramResult.SUCCESS) {
                    throw IllegalStateException("request start temp basal result is failed")
                }

                val updateInfusionInfoResult = infusionInfoRepository.updateTempBasalInfusionInfo(
                    CarelevoTempBasalInfusionInfoDomainModel(
                        infusionId = generateUUID(),
                        address = patchInfo.address,
                        mode = 2,
                        percent = request.percent,
                        speed = request.speed,
                        infusionDurationMin = request.minutes
                    )
                )

                if (!updateInfusionInfoResult) {
                    throw IllegalStateException("update infusion info is failed")
                }

                val updatePatchInfoResult = patchInfoRepository.updatePatchInfo(patchInfo.copy(updatedAt = DateTime.now(), mode = 2))

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
        }.timeout(3000L, TimeUnit.MILLISECONDS).observeOn(Schedulers.io())
    }
}