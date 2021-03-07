package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.pair

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.Id
import info.nightscout.androidaps.utils.extensions.toHex

data class PairResult(val ltk: ByteArray, val podId: Id, val seq: Byte) {
    init {
        require(ltk.size == 16) { "LTK length must be 16 bytes. Received LTK: ${ltk.toHex()}" }
    }
}
