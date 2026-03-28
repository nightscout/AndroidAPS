package app.aaps.pump.omnipod.common.bledriver.comm.pair

import app.aaps.core.utils.toHex

data class PairResult(val ltk: ByteArray, val msgSeq: Byte) {
    init {
        require(ltk.size == 16) { "LTK length must be 16 bytes. Received LTK: ${ltk.toHex()}" }
    }
}
