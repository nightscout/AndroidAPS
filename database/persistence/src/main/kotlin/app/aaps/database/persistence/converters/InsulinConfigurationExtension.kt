package app.aaps.database.persistence.converters

import app.aaps.core.data.model.ICfg
import app.aaps.database.entities.embedments.InsulinConfiguration

fun InsulinConfiguration.fromDb(): ICfg =
    ICfg(
        insulinLabel = this.insulinLabel,
        insulinEndTime = this.insulinEndTime,
        insulinPeakTime = this.insulinPeakTime,
        concentration = this.concentration
    )

fun ICfg.toDb(): InsulinConfiguration =
    InsulinConfiguration(
        insulinLabel = this.insulinLabel,
        insulinEndTime = this.insulinEndTime,
        insulinPeakTime = this.insulinPeakTime,
        concentration = this.concentration
    )