package app.aaps.core.interfaces.insulin

import androidx.annotation.StringRes
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.R
import app.aaps.core.interfaces.resources.ResourceHelper

enum class InsulinType(val value: Int, val insulinEndTime: Long, val insulinPeakTime: Long, @StringRes val label: Int, @StringRes val comment: Int) {
    UNKNOWN(-1, 0, 0, R.string.unknown, R.string.unknown),

    // int FAST_ACTING_INSULIN = 0; // old model no longer available
    // int FAST_ACTING_INSULIN_PROLONGED = 1; // old model no longer available
    OREF_RAPID_ACTING(2, 8 * 3600 * 1000, 75 * 60000, R.string.rapid_acting_oref, R.string.fast_acting_insulin_comment),
    OREF_ULTRA_RAPID_ACTING(3, 8 * 3600 * 1000, 55 * 60000, R.string.ultra_rapid_oref, R.string.ultra_fast_acting_insulin_comment),
    OREF_FREE_PEAK(4, 8 * 3600 * 1000, 50 * 60000, R.string.free_peak_oref, R.string.insulin_peak_time),
    OREF_LYUMJEV(5, 8 * 3600 * 1000, 45 * 60000, R.string.lyumjev,  R.string.lyumjev);

    val iCfg: ICfg
        get() = ICfg(this.name, insulinEndTime, insulinPeakTime, 1.0)

    /** Provide iCfg with a default friendly name on insulin creation from template */
    fun getICfg(rh: ResourceHelper): ICfg = ICfg(rh.gs(this.label), insulinEndTime, insulinPeakTime, 1.0)

    companion object {

        private val map = entries.associateBy(InsulinType::value)
        fun fromInt(type: Int) = map[type] ?:OREF_RAPID_ACTING
        fun fromPeak(insulinPeakTime: Long) = values().firstOrNull {it.insulinPeakTime == insulinPeakTime} ?:OREF_FREE_PEAK
    }
}