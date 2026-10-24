package app.aaps.plugins.sync.nsclientV3

import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * The connection rules of one uploader: on which networks and in which charging state it may send.
 * Nightscout and Tidepool each have their own, so one can upload on cellular while the other waits for
 * WiFi and a charger (#2993). [ConnectivityGate] applies them.
 */
interface ConnectivitySettings {

    val useCellular: Boolean
    val useRoaming: Boolean
    val useWifi: Boolean

    /** Allowed WiFi names, separated by `;`. Empty allows every WiFi. */
    val wifiSsids: String
    val useOnBattery: Boolean
    val useOnCharging: Boolean

    /** Emits when a setting of the network rule changes, but not for the current values. */
    val networkSettingChanges: Flow<Unit>

    /** Emits when a setting of the charging rule changes, but not for the current values. */
    val chargingSettingChanges: Flow<Unit>
}

/** The Nightscout connection settings. */
class NightscoutConnectivitySettings(private val preferences: Preferences) : ConnectivitySettings {

    override val useCellular: Boolean get() = preferences.get(BooleanKey.NsClientUseCellular)
    override val useRoaming: Boolean get() = preferences.get(BooleanKey.NsClientUseRoaming)
    override val useWifi: Boolean get() = preferences.get(BooleanKey.NsClientUseWifi)
    override val wifiSsids: String get() = preferences.get(StringKey.NsClientWifiSsids)
    override val useOnBattery: Boolean get() = preferences.get(BooleanKey.NsClientUseOnBattery)
    override val useOnCharging: Boolean get() = preferences.get(BooleanKey.NsClientUseOnCharging)

    override val networkSettingChanges: Flow<Unit> = merge(
        preferences.observe(BooleanKey.NsClientUseWifi).drop(1).map { },
        preferences.observe(BooleanKey.NsClientUseCellular).drop(1).map { },
        preferences.observe(StringKey.NsClientWifiSsids).drop(1).map { },
        preferences.observe(BooleanKey.NsClientUseRoaming).drop(1).map { }
    )

    override val chargingSettingChanges: Flow<Unit> = merge(
        preferences.observe(BooleanKey.NsClientUseOnCharging).drop(1).map { },
        preferences.observe(BooleanKey.NsClientUseOnBattery).drop(1).map { }
    )
}
