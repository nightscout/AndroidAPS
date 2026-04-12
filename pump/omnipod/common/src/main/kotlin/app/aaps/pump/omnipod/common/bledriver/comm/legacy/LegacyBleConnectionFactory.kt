package app.aaps.pump.omnipod.common.bledriver.comm.legacy

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnection
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnectionFactory
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.session.Connection
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LegacyBleConnectionFactory @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val config: Config,
    private val podState: OmnipodDashPodStateManager
) : BleConnectionFactory {

    private val bluetoothAdapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    override fun createConnection(podAddress: String): BleConnection {
        val adapter = bluetoothAdapter ?: throw ConnectException("Bluetooth not available")
        val podDevice = adapter.getRemoteDevice(podAddress)
        return Connection(podDevice, aapsLogger, config, context, podState)
    }
}
