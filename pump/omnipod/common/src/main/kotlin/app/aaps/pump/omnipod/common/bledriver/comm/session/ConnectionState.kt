package app.aaps.pump.omnipod.common.bledriver.comm.session

import java.util.concurrent.CountDownLatch

sealed class ConnectionState

object Connecting : ConnectionState()
object Connected : ConnectionState()
object NotConnected : ConnectionState()

data class ConnectionWaitCondition(var timeoutMs: Long? = null, val stopConnection: CountDownLatch? = null) {
    init {
        if (timeoutMs == null && stopConnection == null) {
            throw IllegalArgumentException("One of timeoutMs or stopConnection has to be non null")
        }
        if (timeoutMs != null && stopConnection != null) {
            throw IllegalArgumentException("One of timeoutMs or stopConnection has to be null")
        }
    }
}
