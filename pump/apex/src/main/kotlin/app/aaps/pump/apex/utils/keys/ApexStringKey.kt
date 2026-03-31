package app.aaps.pump.apex.utils.keys

import app.aaps.core.keys.BooleanPreferenceKey
import app.aaps.core.keys.StringPreferenceKey
import app.aaps.pump.apex.connectivity.commands.pump.AlarmLength

enum class ApexStringKey(
    override val key: String,
    override val defaultValue: String,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val isPassword: Boolean = false,
    override val isPin: Boolean = false
) : StringPreferenceKey {
    SerialNumber("apex_serial_number", ""),
    LastConnectedSerialNumber("apex_last_connected_serial_number", ""),
    BluetoothAddress("apex_bt_address", ""),
    AlarmSoundLength("apex_alarm_length", AlarmLength.Short.name)
}
