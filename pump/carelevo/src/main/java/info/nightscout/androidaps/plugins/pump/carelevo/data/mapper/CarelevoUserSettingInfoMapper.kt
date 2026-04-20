package info.nightscout.androidaps.plugins.pump.carelevo.data.mapper

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.entities.CarelevoUserSettingInfoEntity
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import org.joda.time.DateTime

internal fun CarelevoUserSettingInfoEntity.transformToCarelevoUserSettingInfoDomainModel() = CarelevoUserSettingInfoDomainModel(
    createdAt = DateTime.parse(createdAt),
    updatedAt = DateTime.parse(updatedAt),
    lowInsulinNoticeAmount = lowInsulinNoticeAmount,
    maxBasalSpeed = maxBasalSpeed,
    maxBolusDose = maxBolusDose,
    needLowInsulinNoticeAmountSyncPatch = needLowInsulinNoticeAmountSyncPatch,
    needMaxBasalSpeedSyncPatch = needMaxBasalSpeedSyncPatch,
    needMaxBolusDoseSyncPatch = needMaxBolusDoseSyncPatch
)

internal fun CarelevoUserSettingInfoDomainModel.transformToCarelevoUserSettingInfoEntity() = CarelevoUserSettingInfoEntity(
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    lowInsulinNoticeAmount = lowInsulinNoticeAmount,
    maxBasalSpeed = maxBasalSpeed,
    maxBolusDose = maxBolusDose,
    needLowInsulinNoticeAmountSyncPatch = needLowInsulinNoticeAmountSyncPatch,
    needMaxBasalSpeedSyncPatch = needMaxBasalSpeedSyncPatch,
    needMaxBolusDoseSyncPatch = needMaxBolusDoseSyncPatch
)