package app.aaps.pump.common.hw.rileylink.ble.defs

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.common.hw.rileylink.R
import java.util.HashMap

enum class RileyLinkEncodingType(val value: Byte, val resourceId: Int?) {
    None(0x00, null),  // No encoding on RL
    Manchester(0x01, null),  // Manchester encoding on RL (for Omnipod)
    FourByteSixByteRileyLink(0x02, R.string.key_medtronic_pump_encoding_4b6b_rileylink),  // 4b6b encoding on RL (for Medtronic)
    FourByteSixByteLocal(0x00, R.string.key_medtronic_pump_encoding_4b6b_local),
    ; // No encoding on RL, but 4b6b encoding in code

    var description: String? = null

    companion object {

        private var encodingTypeMap: MutableMap<String?, RileyLinkEncodingType?>? = null

        private fun doTranslation(rh: ResourceHelper) {
            encodingTypeMap = HashMap<String?, RileyLinkEncodingType?>()

            for (encType in entries) {
                if (encType.resourceId != null) {
                    encodingTypeMap!!.put(rh.gs(encType.resourceId), encType)
                }
            }
        }

        fun getByDescription(description: String?, rh: ResourceHelper): RileyLinkEncodingType {
            if (encodingTypeMap == null) doTranslation(rh)
            if (encodingTypeMap?.containsKey(description) == true) {
                return encodingTypeMap?.get(description)!!
            }

            return FourByteSixByteLocal
        }
    }
}
