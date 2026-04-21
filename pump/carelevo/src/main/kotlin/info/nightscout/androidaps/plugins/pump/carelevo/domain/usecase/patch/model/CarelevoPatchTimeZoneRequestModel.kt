package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.model

import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseRequest

data class CarelevoPatchTimeZoneRequestModel(
    val insulinAmount: Int
) : CarelevoUseCaseRequest