package app.aaps.pump.apex.connectivity.commands.device

import app.aaps.pump.apex.interfaces.ApexDeviceInfo

/** Set basal profile [index] to be used now.
 *
 * * [index] - Basal profile index
 */
class UpdateUsedBasalProfile(
    info: ApexDeviceInfo,
    val index: Int,
) : BaseValueCommand(info) {
    override val valueId = 0x04
    override val isWrite = true

    override val additionalData: ByteArray
        get() = byteArrayOf(index.toByte())

    override fun toString(): String = "UpdateUsedBasalProfile(id = $index)"
}