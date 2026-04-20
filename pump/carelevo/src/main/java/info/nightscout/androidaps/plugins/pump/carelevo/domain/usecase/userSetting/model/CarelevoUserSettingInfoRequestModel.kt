package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.model

import info.nightscout.androidaps.plugins.pump.carelevo.common.model.PatchState
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseRequest

data class CarelevoUserSettingInfoRequestModel(
    val patchState: PatchState? = null,
    val lowInsulinNoticeAmount: Int? = null,
    val maxBasalSpeed: Double? = null,
    val maxBolusDose: Double? = null
) : CarelevoUseCaseRequest