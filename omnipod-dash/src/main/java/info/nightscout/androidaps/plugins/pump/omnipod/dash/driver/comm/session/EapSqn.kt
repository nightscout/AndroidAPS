package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session

import java.nio.ByteBuffer

/***
 * Eap-Aka start session sequence.
 * Incremented for each new session
 */
class EapSqn(var sqn: Long) {

    fun increment(): ByteArray {
        sqn++
        return ByteBuffer.allocate(8)
            .putLong(sqn)
            .array()
            .copyOfRange(2, 8)
    }
}