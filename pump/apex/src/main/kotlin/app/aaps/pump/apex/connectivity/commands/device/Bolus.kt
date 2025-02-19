package app.aaps.pump.apex.connectivity.commands.device

import app.aaps.pump.apex.interfaces.ApexDeviceInfo
import app.aaps.pump.apex.utils.asShortAsByteArray

/** Set bolus.
 *
 * * [dose] - Bolus dose in 0.025U steps
 */
class Bolus(
    info: ApexDeviceInfo,
    val dose: Int,
) : BaseValueCommand(info) {
    override val valueId = 0x12
    override val isWrite = true

    override val additionalData: ByteArray
        get() = dose.asShortAsByteArray() + 0x00.toByte()  // TODO: find out what does zero mean

    override fun toString(): String = "Bolus($dose)"
}