package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.infusion.model

import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseRequest

data class CarelevoDeleteInfusionRequestModel(
    val isDeleteTempBasal: Boolean,
    val isDeleteImmeBolus: Boolean,
    val isDeleteExtendBolus: Boolean
) : CarelevoUseCaseRequest
