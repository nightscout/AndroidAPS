package app.aaps.pump.virtual.keys

import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey

enum class VirtualBooleanNonPreferenceKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val exportable: Boolean = true
) : BooleanNonPreferenceKey {

    IsSuspended("virtual_pump_is_suspended", false),
}