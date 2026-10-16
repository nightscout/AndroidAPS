package app.aaps.pump.carelevo.domain.usecase.bolus

import app.aaps.core.data.model.BS
import app.aaps.pump.carelevo.domain.ext.generateUUID
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoImmeBolusInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import dev.zacsweers.metro.Inject
import kotlin.time.Clock

class CarelevoStartImmeBolusInfusionUseCase @Inject constructor(
    private val patchInfoRepository: CarelevoPatchInfoRepository,
    private val infusionInfoRepository: CarelevoInfusionInfoRepository
) {

    /**
     * Persist a started immediate bolus (`mode=3` + imme-bolus infusion-info + `bolusActionSeq`) after a
     * successful start-imme-bolus write. [expectedTimeSeconds] is the pump-reported expected completion
     * (seeds the synthetic progress loop). [pumpId], [bolusType] and [bolusTotalBefore] let the bolus be recorded
     * later if the app stops before it records it. Returns false with no patch record or if any write fails.
     */
    fun persistImmeBolusStarted(
        actionSeq: Int,
        volume: Double,
        expectedTimeSeconds: Int,
        pumpId: Long,
        bolusType: BS.Type,
        bolusTotalBefore: Double?
    ): Boolean = runCatching {
        val patchInfo = patchInfoRepository.getPatchInfoBySync()
            ?: throw NullPointerException("patch info must be not null")
        require(
            infusionInfoRepository.updateImmeBolusInfusionInfo(
                CarelevoImmeBolusInfusionInfoDomainModel(
                    infusionId = generateUUID(),
                    address = patchInfo.address,
                    mode = 3,
                    volume = volume,
                    infusionDurationSeconds = expectedTimeSeconds,
                    pumpId = pumpId,
                    bolusTotalBefore = bolusTotalBefore,
                    bolusType = bolusType.name
                )
            )
        ) { "update infusion info is failed" }
        require(patchInfoRepository.updatePatchInfo(patchInfo.copy(updatedAt = Clock.System.now(), mode = 3, bolusActionSeq = actionSeq))) { "update patch info is failed" }
    }.isSuccess
}
