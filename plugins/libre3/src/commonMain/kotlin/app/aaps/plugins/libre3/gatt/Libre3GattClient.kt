package app.aaps.plugins.libre3.gatt

/**
 * The Bluetooth link, seen from the driver.
 *
 * ⚠️ ASYNC IMPACT: Android answers on its own binder thread. Everything that has an order, that
 * is connect, discover, turn a channel on, write, must run on one dedicated executor, and only one
 * write may be outstanding at a time. Android 13 and later refuse a second write with an error.
 * The waiting methods block that executor on purpose, so they must never be called from the main
 * thread and never from the thread that reads NFC.
 */
interface Libre3GattClient {

    fun connect(deviceAddress: String)

    /**
     * Drops the link and nothing else.
     *
     * There is no method here to write a command to the sensor's control channel, and there must
     * not be one in this version. See `Libre3DisconnectPolicy`.
     */
    fun disconnect()

    fun isConnected(): Boolean

    /** Turns a channel on or off, and waits for the sensor to confirm. */
    fun setNotify(characteristic: String, enabled: Boolean): Boolean

    /**
     * Writes one whole message, cut into pieces by [Libre3BleFraming], each piece carrying the
     * place it belongs at.
     *
     * For the certificate and the challenge channels only.
     */
    fun write(characteristic: String, payload: ByteArray)

    /**
     * Writes bytes exactly as they are, with no place marker in front.
     *
     * The command channel works this way. A command sent through [write] would arrive as
     * `00 00 11` instead of `11`, and the sensor would not understand it.
     */
    fun writeRaw(characteristic: String, payload: ByteArray)

    /**
     * Waits for one message piece exactly as the sensor sent it.
     *
     * The command channel answers in single pieces that must not be touched, because the first
     * byte is the answer itself and not a counter.
     *
     * @return the piece, or null when the wait ran out or the link dropped.
     */
    fun awaitNotifyRaw(characteristic: String, timeoutMs: Long): ByteArray?

    /**
     * Waits until a channel has delivered a whole message of [exactly] bytes.
     *
     * The pieces carry a counter as their first byte, which is dropped while they are put back
     * together. For the certificate and the challenge channels only.
     *
     * @return the message, or null when the wait ran out or the link dropped.
     */
    fun awaitNotify(characteristic: String, exactly: Int, timeoutMs: Long): ByteArray?

    /**
     * Waits for the next piece from any of the seven data channels of a running session.
     *
     * One stream is used rather than one wait per channel, because a session receives glucose and
     * sensor health messages mixed together and must not miss either.
     *
     * @return which channel it came from, as one of the [Libre3BluetoothUuids] texts, and the
     *   piece as sent. Null when the wait ran out or the link dropped.
     */
    fun awaitDataPlaneNotify(timeoutMs: Long): Pair<String, ByteArray>?
}
