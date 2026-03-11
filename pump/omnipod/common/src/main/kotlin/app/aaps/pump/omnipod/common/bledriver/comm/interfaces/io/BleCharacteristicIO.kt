package app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io

/**
 * Abstraction for BLE characteristic I/O: blocking read, write-with-confirmation,
 * and indication enablement. Implemented by real and fake transports.
 */
interface BleCharacteristicIO {

    /**
     * Block until a packet is received from the characteristic (indication) or timeout.
     * @return Received bytes, or null on timeout/interrupt
     */
    fun receivePacket(timeoutMs: Long = DEFAULT_IO_TIMEOUT_MS): ByteArray?

    /**
     * Send payload and block until write confirmation.
     * @return Success or error
     */
    fun sendAndConfirmPacket(payload: ByteArray): BleSendResult

    /**
     * Drain any pending packets from the incoming queue.
     * @return true if an RTS command was found (CMD characteristic only)
     */
    fun flushIncomingQueue(): Boolean

    /**
     * Enable indications on the characteristic so the pod can send data.
     * @return Success or throws on error
     */
    fun readyToRead(): BleSendResult

    companion object {
        const val DEFAULT_IO_TIMEOUT_MS = 1000L
    }
}
