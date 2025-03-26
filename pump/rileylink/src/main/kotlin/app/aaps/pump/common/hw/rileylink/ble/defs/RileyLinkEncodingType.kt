package app.aaps.pump.common.hw.rileylink.ble.defs

import androidx.annotation.StringRes
import app.aaps.pump.common.hw.rileylink.R

enum class RileyLinkEncodingType(val value: Byte, val key: String?, @StringRes val friendlyName: Int? = null) {
    None(0x00, null),  // No encoding on RL
    Manchester(0x01, null),  // Manchester encoding on RL (for Omnipod)
    FourByteSixByteLocal(0x00, "medtronic_pump_encoding_4b6b_local", R.string.medtronic_pump_encoding_4b6b_rileylink),
    FourByteSixByteRileyLink(0x02, "medtronic_pump_encoding_4b6b_rileylink", R.string.medtronic_pump_encoding_4b6b_local),  // 4b6b encoding on RL (for Medtronic)
    ; // No encoding on RL, but 4b6b encoding in code

    companion object {

        fun getByKey(someKey: String): RileyLinkEncodingType =
            entries.firstOrNull { it.key == someKey } ?: None
    }
}
