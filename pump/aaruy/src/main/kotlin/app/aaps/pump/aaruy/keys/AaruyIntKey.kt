package app.aaps.pump.aaruy.keys

import app.aaps.core.keys.interfaces.IntNonPreferenceKey

enum class AaruyIntKey(
    override val key: String,
    override val defaultValue: Int,
) : IntNonPreferenceKey {

    PumpState("key_aaruy_pump_state", 125),
    PumpLastState("key_aaruy_pump_last_state", 0),
    PumpBolusStep("key_param_bolus_step", 2),
    PumpCloseInjectSound("key_param_close_inject_sound", 0),
    PumpNeedleType("key_param_soft_needle_type", 0),
    PumpType("key_param_pump_type", 0),
}
