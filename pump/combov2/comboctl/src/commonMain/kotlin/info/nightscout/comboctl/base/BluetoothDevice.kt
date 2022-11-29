package info.nightscout.comboctl.base

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Abstract class for operating Bluetooth devices.
 *
 * Subclasses implement blocking IO to allow for RFCOMM-based
 * IO with a Bluetooth device.
 *
 * Subclass instances are created by [BluetoothInterface] subclasses.
 *
 * @param ioDispatcher [CoroutineDispatcher] where the
 *   [blockingSend] and [blockingReceive] calls shall take place.
 *   See the [BlockingComboIO] for details. Typically, this dispatcher
 *   is sent by the [BluetoothInterface] subclass that creates this
 *   [BluetoothDevice] instance.
 */
abstract class BluetoothDevice(ioDispatcher: CoroutineDispatcher) : BlockingComboIO(ioDispatcher) {
    /**
     * The device's Bluetooth address.
     */
    abstract val address: BluetoothAddress

    /**
     * Set up the device's RFCOMM connection.
     *
     * This function blocks until the connection is set up or an error occurs.
     *
     * @throws BluetoothPermissionException if connecting fails
     *         because connection permissions are missing.
     * @throws BluetoothException if connection fails due to an underlying
     *         Bluetooth issue.
     * @throws ComboIOException if connection fails due to an underlying
     *         IO issue and if the device was unpaired.
     * @throws IllegalStateException if this object is in a state
     *         that does not permit connecting, such as a device
     *         that has been shut down.
     */
    abstract fun connect()

    /**
     * Explicitly disconnect the device's RFCOMM connection now.
     *
     * After this call, this BluetoothDevice instance cannot be user
     * anymore until it is reconnected via a new [connect] call.
     */
    abstract fun disconnect()

    /**
     * Unpairs this device.
     *
     * Once this was called, this [BluetoothDevice] instance must not be used anymore.
     * [disconnect] may be called, but will be a no-op. [connect], [send] and [receive]
     * will throw an [IllegalStateException].
     *
     * Calling this while a connection is running leads to undefined behavior.
     * Make sure to call [disconnect] before this function if a connection
     * is currently present.
     */
    abstract fun unpair()
}
