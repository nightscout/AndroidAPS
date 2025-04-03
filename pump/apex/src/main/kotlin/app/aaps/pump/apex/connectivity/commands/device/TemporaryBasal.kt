package app.aaps.pump.apex.connectivity.commands.device

import app.aaps.pump.apex.interfaces.ApexDeviceInfo
import app.aaps.pump.apex.utils.asShortAsByteArray
import app.aaps.pump.apex.utils.toByte

/** Set temporary basal, either absolute or relative.
 *
 * * [isAbsolute] - Is absolute or relative temporary basal?
 * * [duration] - Duration in 15 minute steps
 * * [value] - Dose in 0.025U steps if absolute, percentage if relative
 */
class TemporaryBasal(
    info: ApexDeviceInfo,
    val isAbsolute: Boolean = true,
    val duration: Int,
    val value: Int,
) : BaseValueCommand(info) {
    override val valueId = 0x02
    override val isWrite = true

    override val additionalData: ByteArray
        get() = byteArrayOf(isAbsolute.toByte(), duration.toByte()) + value.asShortAsByteArray()

    override fun toString(): String = "TemporaryBasal(absolute = $isAbsolute, duration = $duration, value = $value)"
}