package app.aaps.pump.apex.connectivity.commands.device

import app.aaps.pump.apex.connectivity.commands.CommandId
import app.aaps.pump.apex.interfaces.ApexDeviceInfo

abstract class BaseValueCommand(info: ApexDeviceInfo) : DeviceCommand(info) {
    /** Value type, default = 0x35 */
    override val type: Int = 0x35

    /** Value ID */
    abstract val valueId: Int

    /** Does command write value? */
    abstract val isWrite: Boolean

    /** Padding value, default - AA */
    open val paddingValue: Int = 0xAA

    /** Additional data after auth block */
    open val additionalData: ByteArray = byteArrayOf()

    override val id: CommandId
        get() = if (isWrite) CommandId.SetValue else CommandId.GetValue

    override val builtData: ByteArray
        get() = byteArrayOf(valueId.toByte(), paddingValue.toByte()) + authBlock + additionalData
}