package app.aaps.pump.carelevo.domain.usecase.basal

import app.aaps.core.interfaces.profile.Profile
import app.aaps.pump.carelevo.domain.ext.generateUUID
import app.aaps.pump.carelevo.domain.ext.splitSegment
import app.aaps.pump.carelevo.domain.model.basal.CarelevoBasalSegmentDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalSegmentInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import org.joda.time.DateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class CarelevoSetBasalProgramUseCase @Inject constructor(
    private val patchInfoRepository: CarelevoPatchInfoRepository,
    private val infusionInfoRepository: CarelevoInfusionInfoRepository
) {

    /**
     * Compute the basal program from [profile] (`getBasalValues` →
     * `splitSegment` → 24 hourly segments → three `chunked(8)` speed lists, seqNo 0/1/2).
     * [BasalProgramPlan.programs] are the wire payloads (v2 sends speed only, not hour/min),
     * [BasalProgramPlan.segments] is the full 24-segment list the infusion-info persist needs.
     */
    fun buildBasalProgramPlan(profile: Profile): BasalProgramPlan {
        val profileBasalSegment = profile.getBasalValues()
        val basalSegment = profileBasalSegment.mapIndexed { index, value ->
            val nextIndex = if (profileBasalSegment.size == index + 1) 0 else index + 1
            val startTimeMinutes = TimeUnit.SECONDS.toMinutes(value.timeAsSeconds.toLong())
            val endTimeMinutes = if (nextIndex == 0) MINUTES_PER_DAY else TimeUnit.SECONDS.toMinutes(profileBasalSegment[nextIndex].timeAsSeconds.toLong())
            CarelevoBasalSegmentDomainModel(
                startTime = startTimeMinutes.toInt(),
                endTime = endTimeMinutes.toInt(),
                speed = value.value
            )
        }.splitSegment()
        val programs = basalSegment.chunked(SEGMENTS_PER_PROGRAM).map { group -> group.map { it.speed } }
        return BasalProgramPlan(programs = programs, segments = basalSegment)
    }

    /**
     * Persist the basal program (`mode=1` + basal infusion-info) after a successful set-basal-program write.
     * Returns false with no patch record or if any write fails.
     */
    fun persistBasalProgram(segments: List<CarelevoBasalSegmentDomainModel>): Boolean = runCatching {
        val patchInfo = patchInfoRepository.getPatchInfoBySync()
            ?: throw NullPointerException("patch info must be not null")
        require(patchInfoRepository.updatePatchInfo(patchInfo.copy(updatedAt = DateTime.now(), mode = 1))) { "update patch info is failed" }
        require(
            infusionInfoRepository.updateBasalInfusionInfo(
                CarelevoBasalInfusionInfoDomainModel(
                    infusionId = generateUUID(),
                    address = patchInfo.address,
                    mode = 1,
                    segments = segments.map {
                        CarelevoBasalSegmentInfusionInfoDomainModel(startTime = it.startTime, endTime = it.endTime, speed = it.speed)
                    },
                    isStop = false
                )
            )
        ) { "update infusion info is failed" }
    }.isSuccess

    /** The three sequential basal-program payloads ([programs], seqNo 0/1/2) plus the full [segments] list. */
    data class BasalProgramPlan(
        val programs: List<List<Double>>,
        val segments: List<CarelevoBasalSegmentDomainModel>
    )

    companion object {

        private const val MINUTES_PER_DAY = 1440L
        private const val SEGMENTS_PER_PROGRAM = 8
    }
}
