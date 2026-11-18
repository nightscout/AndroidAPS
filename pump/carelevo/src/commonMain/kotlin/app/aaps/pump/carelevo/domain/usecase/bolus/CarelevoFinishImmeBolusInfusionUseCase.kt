package app.aaps.pump.carelevo.domain.usecase.bolus

import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.infusion.derivePatchMode
import app.aaps.pump.carelevo.domain.model.result.ResultSuccess
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class CarelevoFinishImmeBolusInfusionUseCase @Inject constructor(
    private val patchInfoRepository: CarelevoPatchInfoRepository,
    private val infusionInfoRepository: CarelevoInfusionInfoRepository
) {

    suspend fun execute(): ResponseResult<CarelevoUseCaseResponse> = withContext(aapsIoDispatcher) {
        runCatching {
            val deleteInfusionInfoResult = infusionInfoRepository.deleteImmeBolusInfusionInfo()
            if (!deleteInfusionInfoResult) {
                throw IllegalStateException("delete imme bolus infusion info is failed")
            }

            val infusionInfo = infusionInfoRepository.getInfusionInfoBySync()
                ?: throw NullPointerException("infusion info must be not null")
            val patchInfo = patchInfoRepository.getPatchInfoBySync()
                ?: throw NullPointerException("patch info must be not null")

            val mode = infusionInfo.derivePatchMode()
                ?: throw NullPointerException("infusion info must be not null")

            val updatePatchInfoResult = patchInfoRepository.updatePatchInfo(
                patchInfo.copy(updatedAt = Clock.System.now(), mode = mode)
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
    }
}