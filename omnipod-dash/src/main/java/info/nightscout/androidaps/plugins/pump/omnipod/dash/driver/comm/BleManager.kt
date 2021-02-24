package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm


import javax.inject.Singleton
import javax.inject.Inject
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.OmnipodDashCommunicationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import info.nightscout.androidaps.logging.AAPSLogger
import android.bluetooth.BluetoothGatt
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.BleIO
import kotlin.Throws
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.ScanFailException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.FailedToConnectException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.CouldNotSendBleException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.BleIOBusyException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.CouldNotConfirmWrite
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.CouldNotEnableNotifications
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.DescriptorNotFoundException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.CouldNotConfirmDescriptorWriteException
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.scan.PodScanner
import android.bluetooth.BluetoothDevice
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.CharacteristicType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.callbacks.BleCommCallbacks
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.BleManager
import android.bluetooth.BluetoothProfile
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.ServiceDiscoverer
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import info.nightscout.androidaps.plugins.pump.omnipod.dash.BuildConfig
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.command.BleCommandHello
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeoutException

@Singleton
class BleManager @Inject constructor(private val context: Context) : OmnipodDashCommunicationManager {

    private val bluetoothAdapter: BluetoothAdapter
    private val bluetoothManager: BluetoothManager

    @Inject lateinit var aapsLogger: AAPSLogger
    private var podAddress: String? = null
    private var gatt: BluetoothGatt? = null
    private var bleio: BleIO? = null
    @Throws(InterruptedException::class, ScanFailException::class, FailedToConnectException::class, CouldNotSendBleException::class, BleIOBusyException::class, TimeoutException::class, CouldNotConfirmWrite::class, CouldNotEnableNotifications::class, DescriptorNotFoundException::class, CouldNotConfirmDescriptorWriteException::class)
    fun activateNewPod() {
        aapsLogger.info(LTag.PUMPBTCOMM, "starting new pod activation")
        val podScanner = PodScanner(aapsLogger, bluetoothAdapter)
        podAddress = podScanner.scanForPod(PodScanner.SCAN_FOR_SERVICE_UUID, PodScanner.POD_ID_NOT_ACTIVATED).scanResult.device.address
        // For tests: this.podAddress = "B8:27:EB:1D:7E:BB";
        connect()
    }

    @Throws(FailedToConnectException::class, CouldNotSendBleException::class, InterruptedException::class, BleIOBusyException::class, TimeoutException::class, CouldNotConfirmWrite::class, CouldNotEnableNotifications::class, DescriptorNotFoundException::class, CouldNotConfirmDescriptorWriteException::class)
    fun connect() {
        // TODO: locking?
        val podDevice = bluetoothAdapter.getRemoteDevice(podAddress)
        var incomingPackets: Map<CharacteristicType, BlockingQueue<ByteArray>> =
            mapOf(CharacteristicType.CMD to LinkedBlockingDeque(),
                CharacteristicType.DATA to LinkedBlockingDeque());
        val bleCommCallbacks = BleCommCallbacks(aapsLogger, incomingPackets)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Connecting to " + podAddress)
        var autoConnect = true
        if (BuildConfig.DEBUG) {
            autoConnect = false
            // TODO: remove this in the future
            // it's easier to start testing from scratch on each run.
        }
        val gatt = podDevice.connectGatt(context, autoConnect, bleCommCallbacks, BluetoothDevice.TRANSPORT_LE)
        this.gatt = gatt

        bleCommCallbacks.waitForConnection(CONNECT_TIMEOUT_MS)
        val connectionState = bluetoothManager.getConnectionState(podDevice, BluetoothProfile.GATT)
        aapsLogger.debug(LTag.PUMPBTCOMM, "GATT connection state: $connectionState")
        if (connectionState != BluetoothProfile.STATE_CONNECTED) {
            throw FailedToConnectException(podAddress)
        }
        val discoverer = ServiceDiscoverer(aapsLogger, gatt, bleCommCallbacks)
        val chars = discoverer.discoverServices()
        bleio = BleIO(aapsLogger, chars, incomingPackets, gatt, bleCommCallbacks)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Saying hello to the pod")
        bleio!!.sendAndConfirmPacket(CharacteristicType.CMD, BleCommandHello(CONTROLLER_ID).asByteArray())
        bleio!!.readyToRead()
    }

    companion object {

        private const val CONNECT_TIMEOUT_MS = 5000
        private const val CONTROLLER_ID = 4242 // TODO read from preferences or somewhere else.
    }

    init {
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
    }
}