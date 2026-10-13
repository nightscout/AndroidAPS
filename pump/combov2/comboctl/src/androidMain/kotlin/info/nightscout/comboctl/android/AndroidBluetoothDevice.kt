package info.nightscout.comboctl.android

import android.content.Context
import info.nightscout.comboctl.base.BluetoothAddress
import info.nightscout.comboctl.base.BluetoothDevice
import info.nightscout.comboctl.base.BluetoothException
import info.nightscout.comboctl.base.BluetoothInterface
import info.nightscout.comboctl.base.ComboIOException
import info.nightscout.comboctl.base.LogLevel
import info.nightscout.comboctl.base.Logger
import info.nightscout.comboctl.utils.retryBlocking
import kotlinx.coroutines.Dispatchers
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import android.bluetooth.BluetoothAdapter as SystemBluetoothAdapter
import android.bluetooth.BluetoothDevice as SystemBluetoothDevice
import android.bluetooth.BluetoothSocket as SystemBluetoothSocket

private val logger = Logger.get("AndroidBluetoothDevice")

/**
 * Class representing a Bluetooth device accessible through Android's Bluetooth API.
 *
 * Users typically do not instantiate this directly. Instead,
 * [AndroidBluetoothInterface]'s implementation of [BluetoothInterface.getDevice]
 * instantiates and returns this (as a [BluetoothDevice]).
 */
class AndroidBluetoothDevice(
    private val androidContext: Context,
    private val systemBluetoothAdapter: SystemBluetoothAdapter,
    override val address: BluetoothAddress
) : BluetoothDevice(Dispatchers.IO) {

    private var systemBluetoothSocket: SystemBluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var canDoIO: Boolean = false
    private var abortConnectAttempt: Boolean = false

    // Use toUpperCase() since Android expects the A-F hex digits in the
    // Bluetooth address string to be uppercase (lowercase ones are considered
    // invalid and cause an exception to be thrown).
    private val androidBtAddressString = address.toString().uppercase(Locale.ROOT)

    // Base class overrides.

    override fun connect() {
        check(systemBluetoothSocket == null) { "Connection already established" }

        logger(LogLevel.DEBUG) { "Attempting to get object representing device with address $address" }

        abortConnectAttempt = false

        lateinit var device: SystemBluetoothDevice

        try {
            // Establishing the RFCOMM connection does not always work right away.
            // Depending on the Android version and the individual Android device,
            // it may require several attempts until the connection is actually
            // established. Some phones behave better in this than others. We
            // also retrieve the BluetoothDevice instance, create an RFCOMM
            // socket, _and_ try to connect in each attempt, since any one of
            // these steps may initially fail.
            // This is kept separate from the for-loop in Pump.connect() on purpose;
            // that loop is in place because the _pump_ may not be ready to connect
            // just yet (for example because the UI is still shown on the LCD), while
            // the retryBlocking loop here is in place because the _Android device_
            // may not be ready to connect right away.
            // When all attempts fail, retryBlocking() lets the exception pass through.
            // That exception is wrapped in BluetoothException, which then needs to be
            // handled by the caller.
            val totalNumAttempts = 5
            retryBlocking(numberOfRetries = totalNumAttempts, delayBetweenRetries = 100) { attemptNumber, previousException ->
                if (abortConnectAttempt)
                    return@retryBlocking

                if (attemptNumber == 0) {
                    logger(LogLevel.DEBUG) { "First attempt to establish an RFCOMM client connection to the Combo" }
                } else {
                    logger(LogLevel.DEBUG) {
                        "Previous attempt to establish an RFCOMM client connection to the Combo failed with" +
                            "exception \"$previousException\"; trying again (this is attempt #${attemptNumber + 1} of 5)"
                    }
                }

                // Give the GC the chance to collect an older BluetoothSocket instance
                // while this thread sleep (see below).
                systemBluetoothSocket = null

                device = systemBluetoothAdapter.getRemoteDevice(androidBtAddressString)

                // Wait for 500 ms until we actually try to connect. This seems to
                // circumvent an as-of-yet unknown Bluetooth related race condition.
                // TODO: Clarify this and wait for whatever is going on there properly.
                try {
                    Thread.sleep(500)
                } catch (ignored: InterruptedException) {
                }

                checkForConnectPermission(androidContext) {
                    systemBluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(Constants.sdpSerialPortUUID)

                    // connect() must be explicitly called. Just creating the socket via
                    // createInsecureRfcommSocketToServiceRecord() does not implicitly
                    // establish the connection. This is important to keep in mind, since
                    // otherwise, the calls below get input and output streams which appear
                    // at first to be OK until their read/write functions are actually used.
                    // At that point, very confusing NullPointerExceptions are thrown from
                    // seemingly nowhere. These NPEs happen because *inside* the streams
                    // there are internal Input/OutputStreams, and *these* are set to null
                    // if the connection wasn't established. See also:
                    // https://stackoverflow.com/questions/24267671/inputstream-read-causes-nullpointerexception-after-having-checked-inputstream#comment37491136_24267671
                    // and: https://stackoverflow.com/a/24269255/560774
                    systemBluetoothSocket!!.connect()
                }
            }
        } catch (t: Throwable) {
            disconnectImpl() // Clean up any partial connection states that may exist.
            throw BluetoothException("Could not establish an RFCOMM client connection to device with address $address", t)
        }

        if (abortConnectAttempt) {
            logger(LogLevel.INFO) { "RFCOMM connection setup with device with address $address aborted" }
            return
        }

        try {
            inputStream = systemBluetoothSocket!!.inputStream
        } catch (e: IOException) {
            disconnectImpl()
            throw ComboIOException("Could not get input stream to device with address $address", e)
        }

        try {
            outputStream = systemBluetoothSocket!!.outputStream
        } catch (e: IOException) {
            disconnectImpl()
            throw ComboIOException("Could not get output stream to device with address $address", e)
        }

        canDoIO = true

        logger(LogLevel.INFO) { "RFCOMM connection with device with address $address established" }
    }

    override fun disconnect() {
        if (systemBluetoothSocket == null) {
            logger(LogLevel.DEBUG) { "Device already disconnected - ignoring redundant call" }
            return
        }

        disconnectImpl()

        logger(LogLevel.INFO) { "RFCOMM connection with device with address $address terminated" }
    }

    override fun unpair() {
        try {
            val device = systemBluetoothAdapter.getRemoteDevice(androidBtAddressString)

            // At time of writing (2021-12-06), the removeBond method
            // is inexplicably still marked with @hide, so we must use
            // reflection to get to it and unpair this device.
            val removeBondMethod = device.javaClass.getMethod("removeBond")
            removeBondMethod.invoke(device)
        } catch (t: Throwable) {
            logger(LogLevel.ERROR) { "Unpairing device with address $address failed with error $t" }
        }
    }

    override fun blockingSend(dataToSend: List<Byte>) {
        // Handle corner case when disconnect() is called in a different coroutine
        // shortly before this function is run.
        if (!canDoIO) {
            logger(LogLevel.DEBUG) { "We are disconnecting; ignoring attempt at sending data" }
            return
        }

        check(outputStream != null) { "Device is not connected - cannot send data" }

        try {
            outputStream!!.write(dataToSend.toByteArray())
        } catch (e: IOException) {
            // If we are disconnecting, don't bother re-throwing the exception;
            // one is always thrown when the stream is closed while write() blocks,
            // and this essentially just means "write() call aborted because the
            // stream got closed". That's not an error.
            if (canDoIO)
                throw ComboIOException("Could not write data to device with address $address", e)
            else
                logger(LogLevel.DEBUG) { "Aborted write call because we are disconnecting" }
        }
    }

    override fun blockingReceive(): List<Byte> {
        // Handle corner case when disconnect() is called in a different coroutine
        // shortly before this function is run.
        if (!canDoIO) {
            logger(LogLevel.DEBUG) { "We are disconnecting; ignoring attempt at receiving data" }
            return listOf()
        }

        check(inputStream != null) { "Device is not connected - cannot receive data" }

        try {
            val buffer = ByteArray(512)
            val numReadBytes = inputStream!!.read(buffer)
            return if (numReadBytes > 0) buffer.toList().subList(0, numReadBytes) else listOf()
        } catch (e: IOException) {
            // If we are disconnecting, don't bother re-throwing the exception;
            // one is always thrown when the stream is closed while read() blocks,
            // and this essentially just means "read() call aborted because the
            // stream got closed". That's not an error.
            if (canDoIO)
                throw ComboIOException("Could not read data from device with address $address", e)
            else {
                logger(LogLevel.DEBUG) { "Aborted read call because we are disconnecting" }
                return listOf()
            }
        }
    }

    private fun disconnectImpl() {
        canDoIO = false
        abortConnectAttempt = true

        if (inputStream != null) {
            try {
                logger(LogLevel.DEBUG) { "Closing input stream" }
                inputStream!!.close()
            } catch (e: IOException) {
                logger(LogLevel.WARN) { "Caught exception while closing input stream to device with address $address: $e - ignoring exception" }
            } finally {
                inputStream = null
            }
        }

        if (outputStream != null) {
            try {
                logger(LogLevel.DEBUG) { "Closing output stream" }
                outputStream!!.close()
            } catch (e: IOException) {
                logger(LogLevel.WARN) { "Caught exception while closing output stream to device with address $address: $e - ignoring exception" }
            } finally {
                outputStream = null
            }
        }

        if (systemBluetoothSocket != null) {
            try {
                logger(LogLevel.DEBUG) { "Closing Bluetooth socket" }
                systemBluetoothSocket!!.close()
            } catch (e: IOException) {
                logger(LogLevel.WARN) { "Caught exception while closing Bluetooth socket to device with address $address: $e - ignoring exception" }
            } finally {
                systemBluetoothSocket = null
            }
        }

        logger(LogLevel.DEBUG) { "Device disconnected" }
    }
}
