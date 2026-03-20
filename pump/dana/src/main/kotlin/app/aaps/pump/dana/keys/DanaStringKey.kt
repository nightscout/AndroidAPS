package app.aaps.pump.dana.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.StringValidator
import app.aaps.pump.dana.R

enum class DanaStringKey(
    override val key: String,
    override val defaultValue: String,
    override val titleResId: Int = 0,
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val isPassword: Boolean = false,
    override val isPin: Boolean = false,
    override val exportable: Boolean = true,
    override val validator: StringValidator = StringValidator.NONE
) : StringPreferenceKey {

    RName("danar_bt_name", "", titleResId = R.string.danar_bt_name_title, preferenceType = PreferenceType.LIST),
    RsName("danars_name", "", titleResId = R.string.selectedpump),
    MacAddress("danars_address", ""),
    Password(
        key = "danars_password",
        defaultValue = "",
        titleResId = R.string.danars_password_title,
        isPassword = true,
        validator = StringValidator.regex("^[A-F0-9]{4}$", "Must be 4 hexadecimal digits (0-9, A-F)")
    ),
    EmulatorDeviceName("danars_emulator_device_name", "", exportable = false),
}
