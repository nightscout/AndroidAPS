package app.aaps.database.persistence.converters

import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.insulin.InsulinType
import app.aaps.database.entities.embedments.InsulinConfiguration

/**
 * [ICfg.isInhaled] has no column - the embedded [InsulinConfiguration] is shared by the boluses,
 * profileSwitches and effectiveProfileSwitches tables, and adding it would be a forward-only Room
 * migration on three tables for a value the stored peak already determines. It is reconstructed on
 * read via [InsulinType.isInhaledPeak], which is unambiguous because the inhaled and injected peak
 * ranges are disjoint, and dropped on write.
 */
fun InsulinConfiguration.fromDb(): ICfg =
    ICfg(
        insulinLabel = this.insulinLabel,
        insulinEndTime = this.insulinEndTime,
        insulinPeakTime = this.insulinPeakTime,
        concentration = this.concentration,
        isInhaled = InsulinType.isInhaledPeak(this.insulinPeakTime)
    )

fun ICfg.toDb(): InsulinConfiguration =
    InsulinConfiguration(
        insulinLabel = this.insulinLabel,
        insulinEndTime = this.insulinEndTime,
        insulinPeakTime = this.insulinPeakTime,
        concentration = this.concentration
    )