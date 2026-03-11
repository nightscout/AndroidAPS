package app.aaps.pump.apex.connectivity.commands.device

import app.aaps.pump.apex.interfaces.ApexDeviceInfo

/** Cancel bolus. */
class CancelBolus(
    info: ApexDeviceInfo,
) : BaseValueCommand(info) {
    override val type = 0x55
    override val valueId = 0x02
    override val isWrite = true

    override val additionalData: ByteArray
        get() = byteArrayOf(0, 0) // TODO: find out what does it mean

    override fun toString(): String = "CancelBolus()"
}