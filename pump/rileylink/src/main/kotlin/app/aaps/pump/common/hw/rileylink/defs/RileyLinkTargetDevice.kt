package app.aaps.pump.common.hw.rileylink.defs

import app.aaps.pump.common.hw.rileylink.R

/**
 * Created by andy on 5/19/18.
 */
enum class RileyLinkTargetDevice(val resourceId: Int, val tuneUpEnabled: Boolean) {

    MedtronicPump(R.string.rileylink_target_device_medtronic, true),
    Omnipod(R.string.rileylink_target_device_omnipod, false);
}
