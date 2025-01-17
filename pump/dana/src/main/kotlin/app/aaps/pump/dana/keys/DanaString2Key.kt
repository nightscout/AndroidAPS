package app.aaps.pump.dana.keys

import app.aaps.core.keys.BooleanPreferenceKey
import app.aaps.core.keys.String2PreferenceKey

enum class DanaString2Key(
    override val key: String,
    override val defaultValue: String,
    override val delimiter: String,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false
) : String2PreferenceKey {

    DanaRsParingKey("danars_pairing_key", "", delimiter = "_"),
    DanaRsV3RandomParingKey("danars_v3_randompairing_key", "", delimiter = "_"),
    DanaRsV3ParingKey("danars_v3_pairing_key", "", delimiter = "_"),
    DanaRsV3RandomSyncKey("danars_v3_randomsync_key", "", delimiter = "_"),
    DanaRsBle5PairingKey("dana_ble5_pairingkey", "", delimiter = ""),
}
