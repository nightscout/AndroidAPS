package app.aaps.pump.omnipod.eros.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class ErosLongNonPreferenceKey(
    override val key: String,
    override val defaultValue: Long,
) : LongNonPreferenceKey {

    TbrsSet("AAPS.Omnipod.tbrs_set", 0L),
    BolusesDelivered("AAPS.Omnipod.std_boluses_delivered", 0L),
    SmbsDelivered("AAPS.Omnipod.smb_boluses_delivered", 0L),
}