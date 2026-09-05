package app.aaps.database.persistence.converters

import app.aaps.core.data.model.BS
import app.aaps.database.entities.Bolus

fun Bolus.fromDb(): BS =
    BS(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        amount = this.amount,
        type = this.type.fromDb(),
        notes = this.notes,
        isBasalInsulin = this.isBasalInsulin,
        iCfg = this.insulinConfiguration.fromDb(),
        ids = this.interfaceIDs.fromDb()
    )

fun BS.toDb(): Bolus =
    Bolus(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        amount = this.amount,
        type = this.type.toDb(),
        notes = this.notes,
        isBasalInsulin = this.isBasalInsulin,
        insulinConfiguration = this.iCfg.toDb(),
        interfaceIDs_backing = this.ids.toDb()
    )

fun Bolus.Type.fromDb(): BS.Type =
    when (this) {
        Bolus.Type.NORMAL  -> BS.Type.NORMAL
        Bolus.Type.SMB     -> BS.Type.SMB
        Bolus.Type.PRIMING -> BS.Type.PRIMING
    }

fun BS.Type.toDb(): Bolus.Type =
    when (this) {
        BS.Type.NORMAL  -> Bolus.Type.NORMAL
        BS.Type.SMB     -> Bolus.Type.SMB
        BS.Type.PRIMING -> Bolus.Type.PRIMING
    }