package app.aaps.plugins.sync.tidepool.keys

import app.aaps.core.keys.KeysStrings
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.sync.SyncStrings

enum class TidepoolBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
) : BooleanPreferenceKey {

    UseTestServers("tidepool_dev_servers", false, title = SyncStrings.title_tidepool_dev_servers, summary = SyncStrings.summary_tidepool_dev_servers),

    // On (the default): Tidepool follows the Nightscout connection settings, as before 4.0. Off: the keys
    // below apply, so for example Tidepool can wait for WiFi and a charger while Nightscout uses cellular (#2993).
    UseNsConnectionSettings("tidepool_use_ns_connection", true, title = SyncStrings.title_tidepool_use_ns_connection, summary = SyncStrings.summary_tidepool_use_ns_connection),
    UseCellular("tidepool_cellular", true, title = KeysStrings.pref_title_ns_use_cellular, negativeDependency = UseNsConnectionSettings),
    UseRoaming("tidepool_allow_roaming", true, title = KeysStrings.pref_title_ns_use_roaming, dependency = UseCellular, negativeDependency = UseNsConnectionSettings),
    UseWifi("tidepool_wifi", true, title = KeysStrings.pref_title_ns_use_wifi, negativeDependency = UseNsConnectionSettings),
    UseOnBattery("tidepool_battery", true, title = KeysStrings.pref_title_ns_use_on_battery, negativeDependency = UseNsConnectionSettings),
    UseOnCharging("tidepool_charging", true, title = KeysStrings.pref_title_ns_use_on_charging, negativeDependency = UseNsConnectionSettings),
    ;

}
