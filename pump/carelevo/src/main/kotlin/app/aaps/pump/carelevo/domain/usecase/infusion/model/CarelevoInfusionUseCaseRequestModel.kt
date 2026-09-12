package app.aaps.pump.carelevo.domain.usecase.infusion.model

import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseRequest

data class CarelevoPumpStopRequestModel(
    val durationMin: Int
) : CarelevoUseCaseRequest