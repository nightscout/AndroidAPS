package app.aaps.core.interfaces.pump.rfcomm

import java.io.InputStream
import java.io.OutputStream

/**
 * Discovered bonded Bluetooth device.
 */
data class RfcommDevice(val name: String, val address: String)

/**
 * Abstraction for Bluetooth Classic RFCOMM communication.
 * Allows swapping real Bluetooth with an emulator for testing.
 */
interface RfcommTransport {

    /**
     * Get a socket for the named bonded device.
     * @return RfcommSocket or null if device not found or permission denied
     */
    fun getSocketForDevice(deviceName: String): RfcommSocket?

    /**
     * List bonded Bluetooth devices matching the Dana name pattern.
     */
    fun getBondedDevices(): List<RfcommDevice>
}

/**
 * Abstraction over BluetoothSocket for RFCOMM serial communication.
 */
interface RfcommSocket {

    val inputStream: InputStream
    val outputStream: OutputStream
    val isConnected: Boolean

    /**
     * Establish the RFCOMM connection. Blocks until connected or throws.
     */
    fun connect()

    /**
     * Close the socket and release resources.
     */
    fun close()
}
