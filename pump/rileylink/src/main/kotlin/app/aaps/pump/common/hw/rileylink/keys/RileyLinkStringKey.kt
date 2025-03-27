package app.aaps.pump.common.hw.rileylink.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

enum class RileyLinkStringKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = true
) : StringNonPreferenceKey {

    Name("pref_rileylink_name", ""),
}