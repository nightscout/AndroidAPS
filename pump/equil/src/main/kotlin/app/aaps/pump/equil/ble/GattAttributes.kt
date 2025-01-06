package app.aaps.pump.equil.ble

import java.util.UUID

object GattAttributes {

    const val SERVICE_RADIO = "0000f000-0000-1000-8000-00805f9b34fb"
    const val NRF_UART_NOTIFY = "0000f001-0000-1000-8000-00805f9b34fb"
    const val NRF_UART_WRITE = "0000f001-0000-1000-8000-00805f9b34fb"
    val characteristicConfigDescriptor: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
