package app.aaps.pump.common.hw.rileylink.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

enum class RileyLinkStringKey(
    override val key: String,
    override val defaultValue: String
) : StringNonPreferenceKey {

    Name("pref_rileylink_name", ""),
}