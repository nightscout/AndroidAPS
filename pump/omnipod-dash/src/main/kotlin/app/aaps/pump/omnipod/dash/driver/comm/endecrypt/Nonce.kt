package app.aaps.pump.omnipod.dash.driver.comm.endecrypt

import java.nio.ByteBuffer

data class Nonce(val prefix: ByteArray, var sqn: Long) {
    init {
        require(prefix.size == 8) { "Nonce prefix should be 8 bytes long" }
    }

    fun increment(podReceiving: Boolean): ByteArray {
        sqn++
        val ret = ByteBuffer.allocate(8)
            .putLong(sqn)
            .array()
            .copyOfRange(3, 8)
        if (podReceiving) {
            ret[0] = (ret[0].toInt() and 127).toByte()
        } else {
            ret[0] = (ret[0].toInt() or 128).toByte()
        }
        return prefix + ret
    }
}
