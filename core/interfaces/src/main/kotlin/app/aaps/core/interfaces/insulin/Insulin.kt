package app.aaps.core.interfaces.insulin

import androidx.annotation.StringRes
import app.aaps.core.data.iob.Iob
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.R
import app.aaps.core.interfaces.configuration.ConfigExportImport

interface Insulin : ConfigExportImport {
    enum class InsulinType(val value: Int, val peak: Int, val dia: Double, @StringRes val label: Int) {
        UNKNOWN(-1, -1, 6.0, R.string.unknown),

        // int FAST_ACTING_INSULIN = 0; // old model no longer available
        // int FAST_ACTING_INSULIN_PROLONGED = 1; // old model no longer available
        OREF_RAPID_ACTING(2, 75, 6.0, R.string.rapid_acting_oref),
        OREF_ULTRA_RAPID_ACTING(3, 55, 6.0, R.string.ultra_rapid_oref),
        OREF_FREE_PEAK(4, 50, 6.0, R.string.free_peak_oref),
        OREF_LYUMJEV(5, 45, 6.0, R.string.lyumjev);

        fun getICfg() = ICfg(this.name, peak, dia)

        companion object {

            private val map = entries.associateBy(InsulinType::value)
            fun fromInt(type: Int) = map[type]
            fun fromPeak(peak: Long) = InsulinType.values().firstOrNull {it.peak == (peak/60000).toInt()} ?:OREF_FREE_PEAK
        }
    }

    val id: InsulinType
    val friendlyName: String
    val dia: Double
    val peak: Int
    fun setDefault(iCfg: ICfg)
    fun insulinList() = ArrayList<CharSequence>()
    fun getOrCreateInsulin(iCfg: ICfg): ICfg
    fun getInsulin(insulinLabel: String): ICfg
    fun iobCalcForTreatment(bolus: BS, time: Long, dia: Double): Iob
    fun iobCalcForTreatment(bolus: BS, time: Long, iCfg: ICfg): Iob

    val iCfg: ICfg
}