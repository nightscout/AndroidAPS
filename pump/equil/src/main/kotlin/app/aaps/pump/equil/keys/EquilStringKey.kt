package app.aaps.pump.equil.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

enum class EquilStringKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = true
) : StringNonPreferenceKey {

    Device("key_equil_devices", ""),
    Password("key_equil_password", ""),
    PairPassword("key_equil_pair_password", ""),
    State("key_equil_state1", ""),
}
