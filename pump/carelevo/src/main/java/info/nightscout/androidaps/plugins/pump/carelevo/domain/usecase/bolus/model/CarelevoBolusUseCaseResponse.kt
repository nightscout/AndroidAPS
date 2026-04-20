package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.model

import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse

data class CancelBolusInfusionResponseModel(
    val infusedAmount : Double,
) : CarelevoUseCaseResponse

data class StartImmeBolusInfusionResponseModel(
    val expectSec : Int
) : CarelevoUseCaseResponse