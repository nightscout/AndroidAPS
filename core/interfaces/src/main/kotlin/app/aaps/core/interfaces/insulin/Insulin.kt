package app.aaps.core.interfaces.insulin

import app.aaps.core.data.iob.Iob
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.configuration.ConfigExportImport
import org.json.JSONObject

interface Insulin : ConfigExportImport {
    enum class InsulinType(val value: Int, val peak: Int, val dia: Double) {
        UNKNOWN(-1, -1, 6.0),

        // int FAST_ACTING_INSULIN = 0; // old model no longer available
        // int FAST_ACTING_INSULIN_PROLONGED = 1; // old model no longer available
        OREF_RAPID_ACTING(2, 75, 6.0),
        OREF_ULTRA_RAPID_ACTING(3, 55, 6.0),
        OREF_FREE_PEAK(4, 50, 6.0),
        OREF_LYUMJEV(5, 45, 6.0);

        companion object {

            private val map = entries.associateBy(InsulinType::value)
            fun fromInt(type: Int) = map[type]
            fun fromPeak(peak: Long) = (InsulinType.values().firstOrNull {it.peak == (peak/60000).toInt()} ?:OREF_FREE_PEAK).value
        }
    }

    val id: InsulinType
    val friendlyName: String
    val comment: String
    val dia: Double
    val peak: Int

    fun iobCalcForTreatment(bolus: BS, time: Long, dia: Double): Iob

    fun iobCalcForTreatment(bolus: BS, time: Long, iCfg: ICfg): Iob

    val iCfg: ICfg
}