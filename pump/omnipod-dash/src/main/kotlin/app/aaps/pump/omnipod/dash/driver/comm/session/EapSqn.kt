package app.aaps.pump.omnipod.dash.driver.comm.session

import java.nio.ByteBuffer

class EapSqn(val value: ByteArray) {
    constructor(v: Long) : this(fromLong(v))

    init {
        require(value.size == SIZE) { "Eap SQN is $SIZE bytes long" }
    }

    fun increment(): EapSqn {
        return EapSqn(toLong() + 1)
    }

    fun toLong(): Long {
        return ByteBuffer.wrap(
            byteArrayOf(0x00, 0x00) +
                value
        ).long
    }

    override fun toString(): String {
        return "EapSqn(value=${toLong()})"
    }

    companion object {

        private const val SIZE = 6
        private fun fromLong(v: Long): ByteArray {
            return ByteBuffer.allocate(8).putLong(v).array().copyOfRange(2, 8)
        }
    }
}
