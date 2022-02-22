package info.nightscout.comboctl.android

import java.util.UUID

object Constants {
    // This is a combination of the base SDP service UUID, which is
    // 00000000-0000-1000-8000-00805F9B34FB, and the short SerialPort
    // UUID, which is 0x1101. The base UUID is specified in the
    // Bluetooth 4.2 spec, Vol 3, Part B, section 2.5 .
    val sdpSerialPortUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")!!
}
