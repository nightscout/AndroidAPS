package app.aaps.pump.diaconn.service

import android.bluetooth.BluetoothAdapter
import android.content.Context
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.shared.tests.ble.BleTransportContract
import org.mockito.kotlin.mock
import java.util.UUID

/** The Diaconn G8 radio code against the shared Bluetooth contract: a Nordic UART service, notify and write apart. */
class DiaconnBleTransportContractTest : BleTransportContract() {

    override val profile = GattProfile(
        service = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),
        notify = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e"),
        write = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    )
    override val advertisedName = "DIACONN-64004"

    override fun createTransport(context: Context, adapter: BluetoothAdapter): BleTransport = DiaconnBleTransportImpl(context, mock())
}
