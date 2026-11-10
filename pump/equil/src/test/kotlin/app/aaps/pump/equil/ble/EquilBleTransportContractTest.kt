package app.aaps.pump.equil.ble

import android.bluetooth.BluetoothAdapter
import android.content.Context
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.shared.tests.ble.BleTransportContract
import org.mockito.kotlin.mock
import java.util.UUID

/** The Equil radio code against the shared Bluetooth contract. Equil notifies and is written on one characteristic. */
class EquilBleTransportContractTest : BleTransportContract() {

    override val profile = GattProfile(
        service = UUID.fromString(GattAttributes.SERVICE_RADIO),
        notify = UUID.fromString(GattAttributes.NRF_UART_NOTIFY),
        write = UUID.fromString(GattAttributes.NRF_UART_WRITE)
    )
    override val advertisedName = "Equil - A12345"

    override fun createTransport(context: Context, adapter: BluetoothAdapter): BleTransport = EquilBleTransportImpl(context, mock())
}
