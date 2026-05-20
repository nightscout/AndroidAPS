package app.aaps.pump.carelevo.domain.usecase.userSetting.model

import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseRequest

data class CarelevoPatchBuzzRequestModel(
    val patchState: PatchState? = null,
    val settingsAlarmBuzz: Boolean? = null
) : CarelevoUseCaseRequest
