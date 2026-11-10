package app.aaps.pump.medtrum.ble

import android.bluetooth.BluetoothAdapter
import android.content.Context
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.shared.tests.ble.BleTransportContract
import org.mockito.kotlin.mock
import java.util.UUID

/**
 * The Medtrum radio code against the shared Bluetooth contract. Medtrum tells its link and data through its own
 * `MedtrumBleCallback` and finds its characteristics by itself after a connect, so only the shared part of the contract
 * runs here.
 */
class MedtrumBleTransportContractTest : BleTransportContract() {

    override val profile = GattProfile(
        service = UUID.fromString("669A9001-0008-968F-E311-6050405558B3"),
        notify = UUID.fromString("669a9120-0008-968f-e311-6050405558b3"),
        write = UUID.fromString("669a9101-0008-968f-e311-6050405558b3")
    )
    override val advertisedName = "MT"
    override val reportsThroughListener = false

    override fun createTransport(context: Context, adapter: BluetoothAdapter): BleTransport = MedtrumBleTransportImpl(mock(), context, mock(), mock())
}
