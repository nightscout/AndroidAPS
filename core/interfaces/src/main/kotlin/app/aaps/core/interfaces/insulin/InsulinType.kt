package app.aaps.core.interfaces.insulin

import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.TextRef

enum class InsulinType(val value: Int, val insulinEndTime: Long, val insulinPeakTime: Long, val label: TextRef, val comment: TextRef) {
    UNKNOWN(-1, 0, 0, InterfacesStrings.unknown, InterfacesStrings.unknown),

    // int FAST_ACTING_INSULIN = 0; // old model no longer available
    // int FAST_ACTING_INSULIN_PROLONGED = 1; // old model no longer available
    OREF_RAPID_ACTING(2, 8 * 3600 * 1000, 75 * 60000, InterfacesStrings.rapid_acting_oref, InterfacesStrings.fast_acting_insulin_comment),
    OREF_ULTRA_RAPID_ACTING(3, 8 * 3600 * 1000, 55 * 60000, InterfacesStrings.ultra_rapid_oref, InterfacesStrings.ultra_fast_acting_insulin_comment),
    OREF_FREE_PEAK(4, 8 * 3600 * 1000, 50 * 60000, InterfacesStrings.free_peak_oref, InterfacesStrings.insulin_peak_time),
    OREF_LYUMJEV(5, 8 * 3600 * 1000, 45 * 60000, InterfacesStrings.lyumjev, InterfacesStrings.lyumjev);

    val iCfg: ICfg
        get() = ICfg(this.name, insulinEndTime, insulinPeakTime, 1.0)

    /** Provide iCfg with a default friendly name on insulin creation from template */
    fun getICfg(rh: ResourceHelper): ICfg = ICfg(rh.gs(this.label), insulinEndTime, insulinPeakTime, 1.0)

    companion object {

        private val map = entries.associateBy(InsulinType::value)
        fun fromInt(type: Int) = map[type] ?: OREF_RAPID_ACTING
        fun fromPeak(insulinPeakTime: Long) = entries.firstOrNull { it.insulinPeakTime == insulinPeakTime } ?: OREF_FREE_PEAK
    }
}
