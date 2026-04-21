package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.model

import info.nightscout.androidaps.plugins.pump.carelevo.common.model.PatchState
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseRequest

data class CarelevoPatchExpiredThresholdModifyRequestModel(
    val patchState: PatchState? = null,
    val patchExpiredThreshold: Int? = null
) : CarelevoUseCaseRequest
