package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.infusion.model

import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseRequest

data class CarelevoPumpStopRequestModel(
    val durationMin: Int
) : CarelevoUseCaseRequest