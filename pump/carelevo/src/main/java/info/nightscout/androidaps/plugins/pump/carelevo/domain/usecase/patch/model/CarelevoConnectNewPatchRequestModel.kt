package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.model

import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseRequest

data class CarelevoConnectNewPatchRequestModel(
    val volume: Int,
    val expiry: Int,
    val remains: Int,
    val maxBasalSpeed: Double,
    val maxVolume: Double,
    val isBuzzOn: Boolean
) : CarelevoUseCaseRequest