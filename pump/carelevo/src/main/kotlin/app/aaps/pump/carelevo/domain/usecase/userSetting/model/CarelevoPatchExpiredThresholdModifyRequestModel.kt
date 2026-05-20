package app.aaps.pump.carelevo.domain.usecase.userSetting.model

import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseRequest

data class CarelevoPatchExpiredThresholdModifyRequestModel(
    val patchState: PatchState? = null,
    val patchExpiredThreshold: Int? = null
) : CarelevoUseCaseRequest
