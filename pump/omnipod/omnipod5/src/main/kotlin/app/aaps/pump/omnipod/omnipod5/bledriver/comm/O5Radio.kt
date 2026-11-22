package app.aaps.pump.omnipod.omnipod5.bledriver.comm

import android.bluetooth.BluetoothManager
import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.device.BleDeviceManager
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnection
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodType
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.legacy.O5BleConnectionFactory
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.util.PodTypeAwarePodScanner
import dev.zacsweers.metro.Inject

/**
 * The radio of the Omnipod 5 driver: all that [O5BleManagerImpl] needs from Bluetooth. The real one is
 * [O5LegacyRadio]; the in-tree pod emulator gives another, so the driver above this line is the same in both cases.
 */
interface O5Radio {

    /** Bonding and the "is Bluetooth there" check. */
    val deviceManager: BleDeviceManager

    /** Scans for one Omnipod 5 pod that is not paired yet, and returns its address. */
    fun scanForNewPod(): String

    /**
     * A connection to the pod at [podAddress]. [controllerId] is the id for the hello packet during a pairing, when
     * the pod state does not hold it yet; null for a pod that is already paired.
     */
    fun createConnection(podAddress: String, controllerId: Long?): BleConnection
}

/** The real radio: the phone's Bluetooth adapter. */
class O5LegacyRadio @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val bleConnectionFactory: O5BleConnectionFactory,
    override val deviceManager: BleDeviceManager
) : O5Radio {

    override fun scanForNewPod(): String {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
            ?: throw ConnectException("Bluetooth not available")
        return PodTypeAwarePodScanner(aapsLogger, adapter).scanForPod(PodType.OMNIPOD_5).address
    }

    override fun createConnection(podAddress: String, controllerId: Long?): BleConnection =
        bleConnectionFactory.createConnection(podAddress, controllerId)
}
