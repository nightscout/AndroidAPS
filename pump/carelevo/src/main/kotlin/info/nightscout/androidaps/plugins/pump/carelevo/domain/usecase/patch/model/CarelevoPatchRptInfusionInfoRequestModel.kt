package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.model

import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseRequest

data class CarelevoPatchRptInfusionInfoRequestModel(
    val runningMinute: Int,
    val remains: Double,
    val infusedTotalBasalAmount: Double,
    val infusedTotalBolusAmount: Double,
    val pumpState: Int,
    val mode: Int,
    val currentInfusedProgramVolume: Double,
    val realInfusedTime: Int
) : CarelevoUseCaseRequest

data class CarelevoPatchRptInfusionInfoDefaultRequestModel(
    val remains: Double,
) : CarelevoUseCaseRequest