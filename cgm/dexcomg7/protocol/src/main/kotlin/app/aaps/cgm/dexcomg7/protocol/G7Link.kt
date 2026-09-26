package app.aaps.cgm.dexcomg7.protocol

/**
 * One open connection to a sensor, as the handshake and the session see it.
 *
 * The Android implementation wraps a BluetoothGatt and runs one GATT operation at a time. Tests use a
 * simulated sensor. Every suspending call throws [G7LinkException] when the operation fails.
 */
interface G7Link {

    /** Bluetooth address of the sensor. */
    val address: String

    /** Name the sensor reports; "DXCMxx" before connecting, "Dexcomxx" after. */
    val name: String?

    val isConnected: Boolean

    /** Turns on notifications (or indications, whichever the characteristic offers). */
    suspend fun enableNotifications(characteristic: G7Characteristic)

    suspend fun write(characteristic: G7Characteristic, value: ByteArray, withResponse: Boolean)

    /**
     * Routes notifications of [characteristic] to [listener], or drops them when null. The listener may
     * be called on any thread.
     */
    fun setListener(characteristic: G7Characteristic, listener: ((ByteArray) -> Unit)?)

    /**
     * Called when the sensor has been asked to bond (0x07 accepted). On Android the sensor normally
     * starts bonding itself; the implementation starts it when it has not.
     */
    suspend fun onBondRequested() {}
}

/**
 * A GATT operation failed. [insufficientAuthentication] is set when the sensor wanted an encrypted link
 * that the phone could not give, which is what a bond the sensor no longer knows looks like.
 */
class G7LinkException(message: String, val insufficientAuthentication: Boolean = false) : Exception(message)
