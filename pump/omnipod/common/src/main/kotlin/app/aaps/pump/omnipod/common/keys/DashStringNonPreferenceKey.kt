package app.aaps.pump.omnipod.common.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

enum class DashStringNonPreferenceKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = true
) : StringNonPreferenceKey {

    PodState("AAPS.OmnipodDash.pod_state", ""),
}
