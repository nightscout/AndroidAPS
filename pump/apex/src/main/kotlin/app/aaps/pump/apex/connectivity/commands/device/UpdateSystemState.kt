package app.aaps.pump.apex.connectivity.commands.device

import app.aaps.pump.apex.interfaces.ApexDeviceInfo
import app.aaps.pump.apex.utils.toByte

/** Update system state, suspend or resume.
 *
 * * [isSuspended] - Suspend system?
 */
class UpdateSystemState(
    info: ApexDeviceInfo,
    val isSuspended: Boolean = true,
) : BaseValueCommand(info) {
    override val valueId = 0x21
    override val isWrite = true

    override val additionalData: ByteArray
        get() = byteArrayOf(isSuspended.toByte())

    override fun toString(): String = "UpdateSystemState(suspended = $isSuspended)"
}