package app.aaps.pump.carelevo.ble

import android.bluetooth.BluetoothAdapter
import android.content.Context
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.pump.carelevo.config.BleEnvConfig
import app.aaps.shared.tests.ble.BleTransportContract
import org.mockito.kotlin.mock
import java.util.UUID

/** The Carelevo radio code against the shared Bluetooth contract. */
class CarelevoBleTransportContractTest : BleTransportContract() {

    override val profile = GattProfile(
        service = UUID.fromString(BleEnvConfig.BLE_SERVICE_UUID),
        notify = UUID.fromString(BleEnvConfig.BLE_TX_CHAR_UUID),
        write = UUID.fromString(BleEnvConfig.BLE_RX_CHAR_UUID)
    )
    override val advertisedName = "CARELEVO"

    override fun createTransport(context: Context, adapter: BluetoothAdapter): BleTransport = CarelevoBleTransportImpl(context, mock())
}
