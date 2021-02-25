package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.ltk

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.BleIO

data class LTK(val ltk: ByteArray, val noncePrefix: ByteArray) {
    init{
        require(ltk.size == 16)
        require(noncePrefix.size == 16)
    }
}

