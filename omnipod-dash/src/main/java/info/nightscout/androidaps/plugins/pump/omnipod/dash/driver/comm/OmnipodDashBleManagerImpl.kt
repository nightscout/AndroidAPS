package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.BuildConfig
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.callbacks.BleCommCallbacks
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.command.BleCommandHello
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.*
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.BleIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.CharacteristicType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.ltk.LTKExchanger
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessageIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.scan.PodScanner
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.status.ConnectionStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.Command
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.Response
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OmnipodDashBleManagerImpl @Inject constructor(private val context: Context, private val aapsLogger: AAPSLogger) : OmnipodDashBleManager {

    private val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter

    @Throws(FailedToConnectException::class, CouldNotSendBleException::class, InterruptedException::class, BleIOBusyException::class, TimeoutException::class, CouldNotConfirmWriteException::class, CouldNotEnableNotifications::class, DescriptorNotFoundException::class, CouldNotConfirmDescriptorWriteException::class)
    private fun connect(podAddress: String): BleIO {
        // TODO: locking?
        val podDevice = bluetoothAdapter.getRemoteDevice(podAddress)
        val incomingPackets: Map<CharacteristicType, BlockingQueue<ByteArray>> =
            mapOf(CharacteristicType.CMD to LinkedBlockingDeque(),
                CharacteristicType.DATA to LinkedBlockingDeque())
        val bleCommCallbacks = BleCommCallbacks(aapsLogger, incomingPackets)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Connecting to $podAddress")
        var autoConnect = true
        if (BuildConfig.DEBUG) {
            autoConnect = false
            // TODO: remove this in the future
            // it's easier to start testing from scratch on each run.
        }
        val gatt = podDevice.connectGatt(context, autoConnect, bleCommCallbacks, BluetoothDevice.TRANSPORT_LE)
        bleCommCallbacks.waitForConnection(CONNECT_TIMEOUT_MS)
        val connectionState = bluetoothManager.getConnectionState(podDevice, BluetoothProfile.GATT)
        aapsLogger.debug(LTag.PUMPBTCOMM, "GATT connection state: $connectionState")
        if (connectionState != BluetoothProfile.STATE_CONNECTED) {
            throw FailedToConnectException(podAddress)
        }
        val discoverer = ServiceDiscoverer(aapsLogger, gatt, bleCommCallbacks)
        val chars = discoverer.discoverServices()
        val bleIO = BleIO(aapsLogger, chars, incomingPackets, gatt, bleCommCallbacks)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Saying hello to the pod")
        bleIO.sendAndConfirmPacket(CharacteristicType.CMD, BleCommandHello(CONTROLLER_ID).data)
        bleIO.readyToRead()
        return bleIO
    }

    override fun sendCommand(cmd: Command): Response {
        TODO("not implemented")
    }

    override fun getStatus(): ConnectionStatus {
        TODO("not implemented")
    }

    @Throws(InterruptedException::class, ScanFailException::class, FailedToConnectException::class, CouldNotSendBleException::class, BleIOBusyException::class, TimeoutException::class, CouldNotConfirmWriteException::class, CouldNotEnableNotifications::class, DescriptorNotFoundException::class, CouldNotConfirmDescriptorWriteException::class)
    override fun connect() {
        // TODO: this is wrong and I know it
        aapsLogger.info(LTag.PUMPBTCOMM, "starting new pod activation")
        val podScanner = PodScanner(aapsLogger, bluetoothAdapter)
        val podAddress = podScanner.scanForPod(PodScanner.SCAN_FOR_SERVICE_UUID, PodScanner.POD_ID_NOT_ACTIVATED).scanResult.device.address
        // For tests: this.podAddress = "B8:27:EB:1D:7E:BB";
        val bleIO = connect(podAddress)
        val msgIO = MessageIO(aapsLogger, bleIO)
        val ltkExchanger = LTKExchanger(aapsLogger, msgIO)
        val ltk = ltkExchanger.negociateLTKAndNonce()

        aapsLogger.info(LTag.PUMPCOMM, "Got LTK and Nonce Prefix: ${ltk}")
    }

    override fun disconnect() {
        TODO("not implemented")
    }


    override fun getPodId(): Id {
        // TODO: return something meaningful here
        return Id.fromInt(4243);
    }

    companion object {

        private const val CONNECT_TIMEOUT_MS = 5000
        const val CONTROLLER_ID = 4242 // TODO read from preferences or somewhere else.
    }

}