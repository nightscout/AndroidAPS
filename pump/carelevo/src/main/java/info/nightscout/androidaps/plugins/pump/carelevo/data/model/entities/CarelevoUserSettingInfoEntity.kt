package info.nightscout.androidaps.plugins.pump.carelevo.data.model.entities

import org.joda.time.DateTime

data class CarelevoUserSettingInfoEntity(
    val createdAt : String = DateTime.now().toString(),
    val updatedAt : String = DateTime.now().toString(),
    val lowInsulinNoticeAmount : Int? = null,
    val maxBasalSpeed : Double? = null,
    val maxBolusDose : Double? = null,
    val needLowInsulinNoticeAmountSyncPatch : Boolean = false,
    val needMaxBasalSpeedSyncPatch : Boolean = false,
    val needMaxBolusDoseSyncPatch : Boolean = false
)