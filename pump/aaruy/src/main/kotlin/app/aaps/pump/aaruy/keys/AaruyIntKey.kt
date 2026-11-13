package app.aaps.pump.aaruy.keys

import app.aaps.core.keys.interfaces.IntNonPreferenceKey

/** Only what the pump cannot tell again after an app restart is kept. */
enum class AaruyIntKey(
    override val key: String,
    override val defaultValue: Int,
    override val exportable: Boolean = true,
) : IntNonPreferenceKey {

    /** The cannula length chosen last time. The pump does not report it. */
    PumpNeedleType("key_param_soft_needle_type", 0),

    /** The model of the paired pump. Records and the pump description need it before the first status. */
    PumpType("key_param_pump_type", 0),
}
