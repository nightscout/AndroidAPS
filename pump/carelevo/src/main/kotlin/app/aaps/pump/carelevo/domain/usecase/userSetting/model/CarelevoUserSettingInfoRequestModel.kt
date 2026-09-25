package app.aaps.pump.carelevo.domain.usecase.userSetting.model

import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseRequest

data class CarelevoUserSettingInfoRequestModel(
    val patchState: PatchState? = null,
    val lowInsulinNoticeAmount: Int? = null,
    val maxBasalSpeed: Double? = null,
    val maxBolusDose: Double? = null
) : CarelevoUseCaseRequest