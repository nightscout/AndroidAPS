package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.pair

import info.nightscout.androidaps.utils.extensions.toHex

data class PairResult(val ltk: ByteArray) {
    init {
        require(ltk.size == 16) { "LTK length must be 16 bytes. Received LTK: ${ltk.toHex()}" }
    }
}
