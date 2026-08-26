package app.aaps.pump.carelevo.domain.usecase.bolus

import app.aaps.pump.carelevo.domain.ext.generateUUID
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoExtendBolusInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoStartExtendBolusInfusionUseCase @Inject constructor(
    private val patchInfoRepository: CarelevoPatchInfoRepository,
    private val infusionInfoRepository: CarelevoInfusionInfoRepository
) {

    /**
     * Persist a started extended bolus (`mode=5` + extend-bolus infusion-info) after a successful
     * start-extend-bolus write. [volume] is the total insulin, [speed] the computed U/h. Returns false with no
     * patch record or if any write fails.
     */
    fun persistExtendBolusStarted(volume: Double, speed: Double, minutes: Int): Boolean = runCatching {
        val patchInfo = patchInfoRepository.getPatchInfoBySync()
            ?: throw NullPointerException("patch info must be not null")
        require(
            infusionInfoRepository.updateExtendBolusInfusionInfo(
                CarelevoExtendBolusInfusionInfoDomainModel(
                    infusionId = generateUUID(),
                    address = patchInfo.address,
                    mode = 5,
                    volume = volume,
                    speed = speed,
                    infusionDurationMin = minutes
                )
            )
        ) { "update infusion info is failed" }
        require(patchInfoRepository.updatePatchInfo(patchInfo.copy(updatedAt = DateTime.now(), mode = 5))) { "update patch info is failed" }
    }.isSuccess
}
