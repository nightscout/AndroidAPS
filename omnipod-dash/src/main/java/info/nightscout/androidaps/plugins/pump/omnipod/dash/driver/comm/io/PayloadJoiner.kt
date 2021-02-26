package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io

import java.io.ByteArrayOutputStream

sealed class PayloadJoinerAction

class PayloadJoinerActionAccept : PayloadJoinerAction()
class PayloadJoinerActionReject(val idx: Byte) : PayloadJoinerAction()

class PayloadJoiner {

    var oneExtra: Boolean = false

    private val payload = ByteArrayOutputStream()

    fun start(payload: ByteArray): Int {
        TODO("not implemented")
    }

    fun accumulate(payload: ByteArray): PayloadJoinerAction {
        TODO("not implemented")
    }

    fun finalize(): PayloadJoinerAction {
        TODO("not implemented")
    }

    fun bytes(): ByteArray {
        TODO("not implemented")
    }
}