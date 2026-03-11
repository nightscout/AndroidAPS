package app.aaps.pump.apex.connectivity.commands.device

import app.aaps.pump.apex.interfaces.ApexDeviceInfo
import app.aaps.pump.apex.utils.asShortAsByteArray

/** Set extended bolus.
 *
 * * [dose] - Bolus dose in 0.025U steps
 * * [duration] - Duration in 15 minute steps
 */
class ExtendedBolus(
    info: ApexDeviceInfo,
    val dose: Int,
    val duration: Int,
) : BaseValueCommand(info) {
    override val valueId = 0x13
    override val isWrite = true

    override val additionalData: ByteArray
        get() = dose.asShortAsByteArray() + duration.asShortAsByteArray()

    override fun toString(): String = "ExtendedBolus(dose = $dose, duration = $duration)"
}