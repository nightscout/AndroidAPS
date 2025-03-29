package app.aaps.pump.apex.connectivity.commands.device

import app.aaps.pump.apex.connectivity.commands.CommandId
import app.aaps.pump.apex.connectivity.ProtocolVersion
import app.aaps.pump.apex.utils.ApexCrypto
import app.aaps.pump.apex.interfaces.ApexDeviceInfo

abstract class DeviceCommand(val info: ApexDeviceInfo) {
    /** Command type, 0x35 or 0x55. See children classes for more info. */
    open val type = 0x35

    /** Minimum protocol version supporting this command */
    open val minProto = ProtocolVersion.PROTO_4_10

    /** Maximum protocol version supporting this command */
    open val maxProto = ProtocolVersion.PROTO_4_11

    /** Command ID */
    abstract val id: CommandId

    /** Constructed data by children */
    abstract val builtData: ByteArray

    /** Serialize command, ready to be sent via BLE */
    fun serialize(): ByteArray {
        val cmdData = builtData
        val header = byteArrayOf(
            type.toByte(),
            (4 + cmdData.size + 2).toByte(),
            0x00,
            id.raw.toByte()
        )
        val data = header + cmdData
        return data + ApexCrypto.crc16(data)
    }

    /** Get common auth block between multiple commands */
    protected val authBlock: ByteArray
        get() = ("APEX" + info.serialNumber).toByteArray()
}
