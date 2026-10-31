package app.aaps.pump.carelevo.domain.model.userSetting

import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import kotlin.time.Clock
import kotlin.time.Instant

data class CarelevoUserSettingInfoDomainModel(
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
    val lowInsulinNoticeAmount: Int? = null,
    val maxBasalSpeed: Double? = null,
    val maxBolusDose: Double? = null,
    val needLowInsulinNoticeAmountSyncPatch: Boolean = false,
    val needMaxBasalSpeedSyncPatch: Boolean = false,
    val needMaxBolusDoseSyncPatch: Boolean = false
) : CarelevoUseCaseResponse