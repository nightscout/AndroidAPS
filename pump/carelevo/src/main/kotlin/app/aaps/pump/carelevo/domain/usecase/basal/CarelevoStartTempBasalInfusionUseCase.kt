package app.aaps.pump.carelevo.domain.usecase.basal

import app.aaps.pump.carelevo.domain.ext.generateUUID
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoTempBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.usecase.basal.model.StartTempBasalInfusionRequestModel
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoStartTempBasalInfusionUseCase @Inject constructor(
    private val patchInfoRepository: CarelevoPatchInfoRepository,
    private val infusionInfoRepository: CarelevoInfusionInfoRepository
) {

    /**
     * Persist a started temp basal (`mode=2` + temp-basal infusion-info) after a successful start-temp-basal
     * write. Returns false with no patch record or if any write fails.
     */
    fun persistTempBasalStarted(request: StartTempBasalInfusionRequestModel): Boolean = runCatching {
        val patchInfo = patchInfoRepository.getPatchInfoBySync()
            ?: throw NullPointerException("patch info must be not null")
        require(
            infusionInfoRepository.updateTempBasalInfusionInfo(
                CarelevoTempBasalInfusionInfoDomainModel(
                    infusionId = generateUUID(),
                    address = patchInfo.address,
                    mode = 2,
                    percent = request.percent,
                    speed = request.speed,
                    infusionDurationMin = request.minutes
                )
            )
        ) { "update infusion info is failed" }
        require(patchInfoRepository.updatePatchInfo(patchInfo.copy(updatedAt = DateTime.now(), mode = 2))) { "update patch info is failed" }
    }.isSuccess
}
