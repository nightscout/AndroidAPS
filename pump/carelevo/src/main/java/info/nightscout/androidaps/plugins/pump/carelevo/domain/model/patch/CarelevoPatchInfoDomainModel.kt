package info.nightscout.androidaps.plugins.pump.carelevo.domain.model.patch

import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import org.joda.time.DateTime

data class CarelevoPatchInfoDomainModel(
    val address: String,
    val createdAt: DateTime = DateTime.now(),
    val updatedAt: DateTime = DateTime.now(),
    val manufactureNumber: String? = null,
    val firmwareVersion: String? = null,
    val bootDateTime: String? = null,
    val bootDateTimeUtcMillis: Long? = null,
    val modelName: String? = null,
    val insulinAmount: Int? = null,
    val insulinRemain: Double? = null,
    val thresholdInsulinRemain: Int? = null,
    val thresholdExpiry: Int? = null,
    val thresholdMaxBasalSpeed: Double? = null,
    val thresholdMaxBolusDose: Double? = null,
    val checkSafety: Boolean? = null,
    val checkNeedle: Boolean? = null,
    val needleFailedCount: Int? = null,
    val isConnected: Boolean? = null,
    val needDiscard: Boolean? = null,
    val isDiscard: Boolean? = null,
    val isExtended: Boolean? = null,
    val isValid: Boolean? = null,
    val isStopped: Boolean? = null,
    val stopMinutes: Int? = null,
    val stopMode: Int? = null,
    val isForceStopped: Boolean? = null,
    val runningMinutes: Int? = null,
    val infusedTotalBasalAmount: Double? = null,
    val infusedTotalBolusAmount: Double? = null,
    val pumpState: Int? = null,
    val mode: Int? = null,
    val bolusActionSeq: Int? = null
) : CarelevoUseCaseResponse

data object NeedleCheckSuccess : CarelevoUseCaseResponse

data class NeedleCheckFailed(
    val failedCount: Int
) : CarelevoUseCaseResponse
