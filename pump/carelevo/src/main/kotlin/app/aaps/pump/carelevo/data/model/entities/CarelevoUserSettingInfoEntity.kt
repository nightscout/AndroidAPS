package app.aaps.pump.carelevo.data.model.entities


data class CarelevoUserSettingInfoEntity(
    val createdAt: String,
    val updatedAt: String,
    val lowInsulinNoticeAmount: Int? = null,
    val maxBasalSpeed: Double? = null,
    val maxBolusDose: Double? = null,
    val needLowInsulinNoticeAmountSyncPatch: Boolean = false,
    val needMaxBasalSpeedSyncPatch: Boolean = false,
    val needMaxBolusDoseSyncPatch: Boolean = false
)