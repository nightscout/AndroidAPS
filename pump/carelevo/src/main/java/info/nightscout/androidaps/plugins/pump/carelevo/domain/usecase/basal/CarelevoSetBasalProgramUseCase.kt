package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.ext.generateUUID
import info.nightscout.androidaps.plugins.pump.carelevo.domain.ext.splitSegment
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.basal.CarelevoBasalSegment
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.basal.CarelevoBasalSegmentDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SetBasalProgramRequestV2
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SetBasalProgramResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SetBasalProgramResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoBasalInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoBasalSegmentInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultSuccess
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoBasalRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.model.SetBasalProgramRequestModel
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.ofType
import io.reactivex.rxjava3.schedulers.Schedulers
import org.joda.time.DateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class CarelevoSetBasalProgramUseCase @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val patchObserver: CarelevoPatchObserver,
    private val basalRepository: CarelevoBasalRepository,
    private val patchInfoRepository: CarelevoPatchInfoRepository,
    private val infusionInfoRepository: CarelevoInfusionInfoRepository
) {

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
                basalRepository.requestSetBasalProgramV2(programRequest1)
                    .blockingGet()
                    .takeIf { it is RequestResult.Pending }
                    ?: throw IllegalStateException("request program1 is not pending")

                aapsLogger.debug(LTag.PUMPCOMM, "requestProgram1.start")

                val requestProgram1Result = patchObserver.basalEvent
                    .ofType<SetBasalProgramResultModel>()
                    .blockingFirst()

                aapsLogger.debug(LTag.PUMPCOMM, "requestProgram1.result result=$requestProgram1Result")

                if (requestProgram1Result.result != SetBasalProgramResult.SUCCESS) {
                    throw IllegalStateException("request program1 result is failed")
                }

                val programRequest2 = requestBasalList[1]
                basalRepository.requestSetBasalProgramV2(programRequest2)
                    .blockingGet()
                    .takeIf { it is RequestResult.Pending }
                    ?: throw IllegalStateException("request program2 is not pending")

                aapsLogger.debug(LTag.PUMPCOMM, "requestProgram2.start")

                val requestProgram2Result = patchObserver.basalEvent
                    .ofType<SetBasalProgramResultModel>()
                    .blockingFirst()

                aapsLogger.debug(LTag.PUMPCOMM, "requestProgram2.result result=$requestProgram2Result")

                if (requestProgram2Result.result != SetBasalProgramResult.SUCCESS) {
                    throw IllegalStateException("request program2 result is failed")
                }

                val programRequest3 = requestBasalList[2]
                basalRepository.requestSetBasalProgramV2(programRequest3)
                    .blockingGet()
                    .takeIf { it is RequestResult.Pending }
                    ?: throw IllegalStateException("request program3 is not pending")

                aapsLogger.debug(LTag.PUMPCOMM, "requestProgram3.start")

                val requestProgram3Result = patchObserver.basalEvent
                    .ofType<SetBasalProgramResultModel>()
                    .blockingFirst()

                aapsLogger.debug(LTag.PUMPCOMM, "requestProgram3.result result=$requestProgram3Result")

                if (requestProgram3Result.result != SetBasalProgramResult.SUCCESS) {
                    throw IllegalStateException("request program3 result is failed")
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
