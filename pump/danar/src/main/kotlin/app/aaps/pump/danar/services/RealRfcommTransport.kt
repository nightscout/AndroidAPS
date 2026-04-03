package app.aaps.pump.danar.services

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.rfcomm.RfcommDevice
import app.aaps.core.interfaces.pump.rfcomm.RfcommSocket
import app.aaps.core.interfaces.pump.rfcomm.RfcommTransport
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealRfcommTransport @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger
) : RfcommTransport {

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    @SuppressLint("MissingPermission")
    override fun getSocketForDevice(deviceName: String): RfcommSocket? {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter ?: return null
        val device = adapter.bondedDevices?.firstOrNull { it.name == deviceName } ?: return null

        return try {
            val btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            BluetoothRfcommSocket(btSocket)
        } catch (e: IOException) {
            aapsLogger.error(LTag.PUMP, "Error creating socket", e)
            null
        }
    }

    @SuppressLint("MissingPermission")
    override fun getBondedDevices(): List<RfcommDevice> {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        return try {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            adapter?.bondedDevices
                ?.filter { it.name != null }
                ?.map { RfcommDevice(name = it.name, address = it.address) }
                ?.sortedBy { it.name }
                ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }
}

/**
 * Wrapper around Android BluetoothSocket implementing RfcommSocket interface.
 */
@SuppressLint("MissingPermission")
private class BluetoothRfcommSocket(
    private val btSocket: BluetoothSocket
) : RfcommSocket {

    override val inputStream: InputStream get() = btSocket.inputStream
    override val outputStream: OutputStream get() = btSocket.outputStream
    override val isConnected: Boolean get() = btSocket.isConnected

    override fun connect() = btSocket.connect()

    override fun close() = btSocket.close()
}
