package app.aaps.pump.aaruy.comm

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.shared.tests.ble.BleTransportContract
import org.mockito.kotlin.mock
import java.util.UUID

/** The Aaruy radio code against the shared Bluetooth contract. Aaruy writes without response and scans for its own name. */
class AaruyBleTransportContractTest : BleTransportContract() {

    override val profile = GattProfile(
        service = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),
        notify = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e"),
        write = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"),
        writeProperties = BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
    )
    override val advertisedName = "aaruy_X10001Y"

    override fun createTransport(context: Context, adapter: BluetoothAdapter): BleTransport = AaruyBleTransportImpl(context, mock())
}
