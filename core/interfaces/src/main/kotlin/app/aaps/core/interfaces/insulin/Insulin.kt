package app.aaps.core.interfaces.insulin

import app.aaps.core.interfaces.configuration.ConfigExportImport
import app.aaps.core.interfaces.iob.Iob
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.embedments.InsulinConfiguration

interface Insulin : ConfigExportImport {

    enum class InsulinType(val value: Int) {
        UNKNOWN(-1),

        // int FAST_ACTING_INSULIN = 0; // old model no longer available
        // int FAST_ACTING_INSULIN_PROLONGED = 1; // old model no longer available
        OREF_RAPID_ACTING(2),
        OREF_ULTRA_RAPID_ACTING(3),
        OREF_FREE_PEAK(4),
        OREF_LYUMJEV(5);

        companion object {

            private val map = values().associateBy(InsulinType::value)
            fun fromInt(type: Int) = map[type]
        }
    }

    val id: InsulinType
    val friendlyName: String
    val comment: String
    val dia: Double
    val peak: Int

    fun iobCalcForTreatment(bolus: Bolus, time: Long, dia: Double): Iob

    val insulinConfiguration: InsulinConfiguration
}