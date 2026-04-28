package app.aaps.pump.carelevo.domain.usecase.patch.model

import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseRequest

data class CarelevoPatchTimeZoneRequestModel(
    val insulinAmount: Int
) : CarelevoUseCaseRequest