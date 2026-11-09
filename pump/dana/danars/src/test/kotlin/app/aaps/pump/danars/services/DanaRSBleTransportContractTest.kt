package app.aaps.pump.danars.services

import android.bluetooth.BluetoothAdapter
import android.content.Context
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.shared.tests.ble.BleTransportContract
import org.mockito.kotlin.mock
import java.util.UUID

/** The Dana RS radio code against the shared Bluetooth contract. Dana finds its characteristics in any service. */
class DanaRSBleTransportContractTest : BleTransportContract() {

    override val profile = GattProfile(
        service = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"),
        notify = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb"),
        write = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
    )
    override val advertisedName = "UHH00002TI"

    override fun createTransport(context: Context, adapter: BluetoothAdapter): BleTransport = BleTransportImpl(context, mock(), adapter)
}
