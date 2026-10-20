package app.aaps.pump.ap550s.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

/**
 * Text values the AP550 driver stores but does not show on its own preference screen: the identity
 * of the paired pump. They are written by the pairing screen and read by the driver and the
 * execution service.
 */
enum class Ap550StringNonKey(
    override val key: String,
    override val defaultValue: String
) : StringNonPreferenceKey {

    /** Bluetooth device name of the paired pump, as chosen on the pairing screen. */
    BtDeviceName("pref_ap550_bt_device_name", ""),

    /** Bluetooth address of the paired pump. */
    MacAddress("pref_ap550_mac_address", ""),

    /** Serial number read from the paired pump. */
    SerialNumber("pref_ap550_ser_number", ""),

    /**
     * Pairing code shown on the pump and typed in once. The authentication handshake sends it again
     * after a pump restart, so it is kept for as long as the pump is paired.
     *
     * The key is the wording the old screen used, not a `pref_...` name, so the code keeps being
     * found under the storage key it was always written to.
     */
    PairingCode("Please input pairing code", ""),
}