package app.aaps.pump.dana.keys

import app.aaps.core.keys.interfaces.StringComposedNonPreferenceKey

enum class DanaStringComposedKey(
    override val key: String,
    override val format: String = "%s",
    override val defaultValue: String,
) : StringComposedNonPreferenceKey {

    DanaRsParingKey(key = "danars_pairing_key_", defaultValue = ""),
    DanaRsV3RandomParingKey(key = "danars_v3_randompairing_key_", defaultValue = ""),
    DanaRsV3ParingKey(key = "danars_v3_pairing_key_", defaultValue = ""),
    DanaRsV3RandomSyncKey(key = "danars_v3_randomsync_key_", defaultValue = ""),
    DanaRsBle5PairingKey(key = "dana_ble5_pairingkey", defaultValue = ""),
}
