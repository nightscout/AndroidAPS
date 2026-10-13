package app.aaps.pump.omnipod.eros.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

enum class ErosStringNonPreferenceKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = true
) : StringNonPreferenceKey {

    PodState("AAPS.Omnipod.pod_state", ""),
    ActiveBolus("AAPS.Omnipod.current_bolus", ""),
}