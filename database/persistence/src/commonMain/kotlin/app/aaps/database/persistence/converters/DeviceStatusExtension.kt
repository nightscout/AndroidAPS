package app.aaps.database.persistence.converters

import app.aaps.core.data.model.DS
import app.aaps.database.entities.DeviceStatus

fun DeviceStatus.fromDb(): DS =
    DS(
        id = this.id,
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        ids = this.interfaceIDs.fromDb(),
        device = this.device,
        pump = this.pump,
        enacted = this.enacted,
        suggested = this.suggested,
        iob = this.iob,
        uploaderBattery = this.uploaderBattery,
        isCharging = this.isCharging,
        configuration = this.configuration
    )

fun DS.toDb(): DeviceStatus =
    DeviceStatus(
        id = this.id,
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        interfaceIDs_backing = this.ids.toDb(),
        device = this.device,
        pump = this.pump,
        enacted = this.enacted,
        suggested = this.suggested,
        iob = this.iob,
        uploaderBattery = this.uploaderBattery,
        isCharging = this.isCharging,
        configuration = this.configuration
    )
