package info.nightscout.androidaps.plugins.pump.carelevo.domain.model.userSetting

import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import org.joda.time.DateTime

data class CarelevoUserSettingInfoDomainModel(
    val createdAt : DateTime = DateTime.now(),
    val updatedAt : DateTime = DateTime.now(),
    val lowInsulinNoticeAmount : Int? = null,
    val maxBasalSpeed : Double? = null,
    val maxBolusDose : Double? = null,
    val needLowInsulinNoticeAmountSyncPatch : Boolean = false,
    val needMaxBasalSpeedSyncPatch : Boolean = false,
    val needMaxBolusDoseSyncPatch : Boolean = false
) : CarelevoUseCaseResponse