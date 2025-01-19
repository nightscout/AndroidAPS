package app.aaps.pump.common.hw.rileylink.ble.defs

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.common.hw.rileylink.R

enum class RileyLinkEncodingType(val value: Byte, val resourceId: Int?) {
    None(0x00, null),  // No encoding on RL
    Manchester(0x01, null),  // Manchester encoding on RL (for Omnipod)
    FourByteSixByteRileyLink(0x02, R.string.key_medtronic_pump_encoding_4b6b_rileylink),  // 4b6b encoding on RL (for Medtronic)
    FourByteSixByteLocal(0x00, R.string.key_medtronic_pump_encoding_4b6b_local),
    ; // No encoding on RL, but 4b6b encoding in code

    companion object {

        fun getByDescription(description: String?, rh: ResourceHelper): RileyLinkEncodingType =
            entries.find {
                if (it.resourceId != null) rh.gs(it.resourceId) == description else false
            } ?: FourByteSixByteLocal
    }
}
