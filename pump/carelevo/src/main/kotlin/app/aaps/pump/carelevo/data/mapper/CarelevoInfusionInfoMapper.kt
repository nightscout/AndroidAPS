package app.aaps.pump.carelevo.data.mapper

import app.aaps.pump.carelevo.data.model.entities.CarelevoBasalInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoBasalSegmentInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoExtendBolusInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoImmeBolusInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoTempBasalInfusionInfoEntity
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalSegmentInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoExtendBolusInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoImmeBolusInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoTempBasalInfusionInfoDomainModel
import org.joda.time.DateTime

internal fun CarelevoBasalSegmentInfusionInfoEntity.transformToCarelevoBasalSegmentInfusionInfoDomainModel() = CarelevoBasalSegmentInfusionInfoDomainModel(
    createdAt = DateTime.parse(createdAt),
    updatedAt = DateTime.parse(updatedAt),
    startTime = startTime,
    endTime = endTime,
    speed = speed
)

internal fun CarelevoBasalSegmentInfusionInfoDomainModel.transformToCarelevoBasalSegmentInfusionInfoEntity() = CarelevoBasalSegmentInfusionInfoEntity(
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    startTime = startTime,
    endTime = endTime,
    speed = speed
)

internal fun CarelevoBasalInfusionInfoEntity.transformToCarelevoBasalInfusionInfoDomainModel() = CarelevoBasalInfusionInfoDomainModel(
    infusionId = infusionId,
    address = address,
    mode = mode,
    createdAt = DateTime.parse(createdAt),
    updatedAt = DateTime.parse(updatedAt),
    segments = segments.map { it.transformToCarelevoBasalSegmentInfusionInfoDomainModel() },
    isStop = isStop
)

internal fun CarelevoBasalInfusionInfoDomainModel.transformToCarelevoBasalInfusionInfoEntity() = CarelevoBasalInfusionInfoEntity(
    infusionId = infusionId,
    address = address,
    mode = mode,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    segments = segments.map { it.transformToCarelevoBasalSegmentInfusionInfoEntity() },
    isStop = isStop
)

internal fun CarelevoTempBasalInfusionInfoEntity.transformToCarelevoTempBasalInfusionInfoDomainModel() = CarelevoTempBasalInfusionInfoDomainModel(
    infusionId = infusionId,
    address = address,
    mode = mode,
    createdAt = DateTime.parse(createdAt),
    updatedAt = DateTime.parse(updatedAt),
    percent = percent,
    speed = speed,
    infusionDurationMin = infusionDurationMin
)

internal fun CarelevoTempBasalInfusionInfoDomainModel.transformToCarelevoTempBasalInfusionInfoEntity() = CarelevoTempBasalInfusionInfoEntity(
    infusionId = infusionId,
    address = address,
    mode = mode,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    percent = percent,
    speed = speed,
    infusionDurationMin = infusionDurationMin
)

internal fun CarelevoImmeBolusInfusionInfoEntity.transformToCarelevoImmeBolusInfusionInfoDomainModel() = CarelevoImmeBolusInfusionInfoDomainModel(
    infusionId = infusionId,
    address = address,
    mode = mode,
    createdAt = DateTime.parse(createdAt),
    updatedAt = DateTime.parse(updatedAt),
    volume = volume,
    infusionDurationSeconds = infusionDurationSeconds
)

internal fun CarelevoImmeBolusInfusionInfoDomainModel.transformToCarelevoImmeBolusInfusionInfoEntity() = CarelevoImmeBolusInfusionInfoEntity(
    infusionId = infusionId,
    address = address,
    mode = mode,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    volume = volume,
    infusionDurationSeconds = infusionDurationSeconds
)

internal fun CarelevoExtendBolusInfusionInfoEntity.transformToCarelevoExtendBolusInfusionInfoDomainModel() = CarelevoExtendBolusInfusionInfoDomainModel(
    infusionId = infusionId,
    address = address,
    mode = mode,
    createdAt = DateTime.parse(createdAt),
    updatedAt = DateTime.parse(updatedAt),
    volume = volume,
    speed = speed,
    infusionDurationMin = infusionDurationMin
)

internal fun CarelevoExtendBolusInfusionInfoDomainModel.transformToCarelevoExtendBolusInfusionInfoEntity() = CarelevoExtendBolusInfusionInfoEntity(
    infusionId = infusionId,
    address = address,
    mode = mode,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    volume = volume,
    speed = speed,
    infusionDurationMin = infusionDurationMin
)

internal fun CarelevoInfusionInfoEntity.transformToCarelevoInfusionInfoDomainModel() = CarelevoInfusionInfoDomainModel(
    basalInfusionInfo = basalInfusionInfo?.transformToCarelevoBasalInfusionInfoDomainModel(),
    tempBasalInfusionInfo = tempBasalInfusionInfo?.transformToCarelevoTempBasalInfusionInfoDomainModel(),
    immeBolusInfusionInfo = immeBolusInfusionInfo?.transformToCarelevoImmeBolusInfusionInfoDomainModel(),
    extendBolusInfusionInfo = extendBolusInfusionInfo?.transformToCarelevoExtendBolusInfusionInfoDomainModel()
)

internal fun CarelevoInfusionInfoDomainModel.transformToCarelevoInfusionInfoEntity() = CarelevoInfusionInfoEntity(
    basalInfusionInfo = basalInfusionInfo?.transformToCarelevoBasalInfusionInfoEntity(),
    tempBasalInfusionInfo = tempBasalInfusionInfo?.transformToCarelevoTempBasalInfusionInfoEntity(),
    immeBolusInfusionInfo = immeBolusInfusionInfo?.transformToCarelevoImmeBolusInfusionInfoEntity(),
    extendBolusInfusionInfo = extendBolusInfusionInfo?.transformToCarelevoExtendBolusInfusionInfoEntity()
)