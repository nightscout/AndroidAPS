package app.aaps.plugins.sync.tidepool.keys

import app.aaps.core.keys.KeysStrings
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.TextRef

enum class TidepoolStringKey(
    override val key: String,
    override val defaultValue: String,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val isPassword: Boolean = false,
    override val isPin: Boolean = false,
) : StringPreferenceKey {

    // Used only when TidepoolBooleanKey.UseNsConnectionSettings is off
    WifiSsids(
        "tidepool_wifi_ssids", "", title = KeysStrings.ns_wifi_ssids, summary = KeysStrings.ns_wifi_ssids_summary,
        dependency = TidepoolBooleanKey.UseWifi, negativeDependency = TidepoolBooleanKey.UseNsConnectionSettings
    ),
    ;
}
