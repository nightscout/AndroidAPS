package app.aaps.pump.carelevo.data.mapper

import app.aaps.pump.carelevo.ext.parseIsoInstant
import app.aaps.pump.carelevo.data.model.entities.CarelevoUserSettingInfoEntity
import app.aaps.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel

internal fun CarelevoUserSettingInfoEntity.transformToCarelevoUserSettingInfoDomainModel() = CarelevoUserSettingInfoDomainModel(
    createdAt = parseIsoInstant(createdAt),
    updatedAt = parseIsoInstant(updatedAt),
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