package app.aaps.pump.medtronic.keys

import app.aaps.core.keys.IntNonPreferenceKey

enum class MedtronicIntNonKey(
    override val key: String,
    override val defaultValue: Int,
) : IntNonPreferenceKey {

    TbrsSet("medtronic_tbrs_set", 0),
    SmbBoluses("medtronic_smb_boluses_delivered", 0),
    StandardBoluses("medtronic_std_boluses_delivered", 0),
}