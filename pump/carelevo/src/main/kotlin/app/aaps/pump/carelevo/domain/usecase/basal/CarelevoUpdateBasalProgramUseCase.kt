package app.aaps.pump.carelevo.domain.usecase.basal

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.carelevo.domain.CarelevoPatchObserver
import app.aaps.pump.carelevo.domain.ext.generateUUID
import app.aaps.pump.carelevo.domain.ext.splitSegment
import app.aaps.pump.carelevo.domain.model.RequestResult
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.basal.CarelevoBasalSegment
import app.aaps.pump.carelevo.domain.model.basal.CarelevoBasalSegmentDomainModel
import app.aaps.pump.carelevo.domain.model.bt.SetBasalProgramRequestV2
import app.aaps.pump.carelevo.domain.model.bt.SetBasalProgramResult
import app.aaps.pump.carelevo.domain.model.bt.UpdateBasalProgramAdditionalResultModel
import app.aaps.pump.carelevo.domain.model.bt.UpdateBasalProgramResultModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalSegmentInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.result.ResultSuccess
import app.aaps.pump.carelevo.domain.repository.CarelevoBasalRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import app.aaps.pump.carelevo.domain.usecase.basal.model.SetBasalProgramRequestModel
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.joda.time.DateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class CarelevoUpdateBasalProgramUseCase @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val patchObserver: CarelevoPatchObserver,
    private val basalRepository: CarelevoBasalRepository,
    private val patchInfoRepository: CarelevoPatchInfoRepository,
    private val infusionInfoRepository: CarelevoInfusionInfoRepository
) {

    companion object {

        private const val BASAL_RESPONSE_TIMEOUT_SECONDS = 8L
    }

    fun execute(request: CarelevoUseCaseRequest): Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                if (request !is SetBasalProgramRequestModel) {
                    throw IllegalArgumentException("request is not SetBasalProgramRequestModel")
                }

                val profileBasalSegment = request.profile.getBasalValues()
                val basalSegment = profileBasalSegment.mapIndexed { index, value ->
                    val nextIndex = if (profileBasalSegment.size == index + 1) {
                        0
                    } else {
                        index + 1
                    }
                    val startTimeMinutes = TimeUnit.SECONDS.toMinutes(value.timeAsSeconds.toLong())
                    val endTimeMinutes = if (nextIndex == 0) {
                        1440
                    } else {
                        TimeUnit.SECONDS.toMinutes(profileBasalSegment[nextIndex].timeAsSeconds.toLong())
                    }
                    CarelevoBasalSegmentDomainModel(
                        startTime = startTimeMinutes.toInt(),
                        endTime = endTimeMinutes.toInt(),
                        speed = value.value
                    )
                }.splitSegment()

                aapsLogger.debug(LTag.PUMPCOMM, "splitSegment result=$basalSegment")

                val requestBasalList = basalSegment
                    .chunked(8)
                    .mapIndexed { index, group ->
                        val segmentGroup = group.map {
                            CarelevoBasalSegment(
                                injectStartHour = 1,
                                injectStartMin = 0,
                                injectSpeed = it.speed
                            )
                        }
                        SetBasalProgramRequestV2(
                            seqNo = index,
                            segmentList = segmentGroup
                        )
                    }

                aapsLogger.debug(LTag.PUMPCOMM, "buildRequestList result=$requestBasalList")

                val programRequest1 = requestBasalList[0]
                val requestProgram1ResultFuture = patchObserver.basalEvent
                    .filter { it is UpdateBasalProgramResultModel || it is UpdateBasalProgramAdditionalResultModel }
                    .map {
                        when (it) {
                            is UpdateBasalProgramResultModel           -> it.result
                            is UpdateBasalProgramAdditionalResultModel -> it.result
                            else                                       -> throw IllegalStateException("Unexpected basal ack type")
                        }
                    }
                    .firstOrError()
                    .timeout(BASAL_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .toFuture()
                basalRepository.requestUpdateBasalProgramV2(programRequest1)
                    .blockingGet()
                    .takeIf { it is RequestResult.Pending }
                    ?: throw IllegalStateException("request update program1 is not pending")

                aapsLogger.debug(LTag.PUMPCOMM, "requestProgram1.start")

                val requestProgram1Result = requestProgram1ResultFuture.get()

                aapsLogger.debug(LTag.PUMPCOMM, "requestProgram1.result result=$requestProgram1Result")

                if (requestProgram1Result != SetBasalProgramResult.SUCCESS) {
                    throw IllegalStateException("request update program1 result is failed")
                }

                val programRequest2 = requestBasalList[1]
                val requestProgram2ResultFuture = patchObserver.basalEvent
                    .filter { it is UpdateBasalProgramResultModel || it is UpdateBasalProgramAdditionalResultModel }
                    .map {
                        when (it) {
                            is UpdateBasalProgramResultModel           -> it.result
                            is UpdateBasalProgramAdditionalResultModel -> it.result
                            else                                       -> throw IllegalStateException("Unexpected basal ack type")
                        }
                    }
                    .firstOrError()
                    .timeout(BASAL_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .toFuture()
                basalRepository.requestUpdateBasalProgramV2(programRequest2)
                    .blockingGet()
                    .takeIf { it is RequestResult.Pending }
                    ?: throw IllegalStateException("request update program2 is not pending")

                aapsLogger.debug(LTag.PUMPCOMM, "requestProgram2.start")

                val requestProgram2Result = requestProgram2ResultFuture.get()

                aapsLogger.debug(LTag.PUMPCOMM, "requestProgram2.result result=$requestProgram2Result")

                if (requestProgram2Result != SetBasalProgramResult.SUCCESS) {
                    throw IllegalStateException("request update program2 result is failed")
                }

                val programRequest3 = requestBasalList[2]
                val requestProgram3ResultFuture = patchObserver.basalEvent
                    .filter { it is UpdateBasalProgramResultModel || it is UpdateBasalProgramAdditionalResultModel }
                    .map {
                        when (it) {
                            is UpdateBasalProgramResultModel           -> it.result
                            is UpdateBasalProgramAdditionalResultModel -> it.result
                            else                                       -> throw IllegalStateException("Unexpected basal ack type")
                        }
                    }
                    .firstOrError()
                    .timeout(BASAL_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .toFuture()
                basalRepository.requestUpdateBasalProgramV2(programRequest3)
                    .blockingGet()
                    .takeIf { it is RequestResult.Pending }
                    ?: throw IllegalStateException("request update program3 is not pending")

                aapsLogger.debug(LTag.PUMPCOMM, "requestProgram3.start")

                val requestProgram3Result = requestProgram3ResultFuture.get()

                aapsLogger.debug(LTag.PUMPCOMM, "requestProgram3.result result=$requestProgram3Result")

                if (requestProgram3Result != SetBasalProgramResult.SUCCESS) {
                    throw IllegalStateException("request update program3 result is failed")
                }

                val patchInfo = patchInfoRepository.getPatchInfoBySync()
                    ?: throw NullPointerException("patch info must be not null")

                val updatePatchInfoResult = patchInfoRepository.updatePatchInfo(patchInfo.copy(updatedAt = DateTime.now(), mode = 1))

                aapsLogger.debug(LTag.PUMPCOMM, "updatePatchInfo result=$updatePatchInfoResult")

                if (!updatePatchInfoResult) {
                    throw IllegalStateException("update patch info is failed")
                }

                val updateInfusionInfoResult = infusionInfoRepository.updateBasalInfusionInfo(
                    CarelevoBasalInfusionInfoDomainModel(
                        infusionId = generateUUID(),
                        address = patchInfo.address,
                        mode = 1,
                        segments = basalSegment.map {
                            CarelevoBasalSegmentInfusionInfoDomainModel(
                                startTime = it.startTime,
                                endTime = it.endTime,
                                speed = it.speed
                            )
                        },
                        isStop = false
                    )
                )

                aapsLogger.debug(LTag.PUMPCOMM, "updateInfusionInfo result=$updateInfusionInfoResult")

                if (!updateInfusionInfoResult) {
                    throw IllegalStateException("update infusion info is failed")
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
