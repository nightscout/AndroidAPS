package app.aaps.pump.apex.connectivity.commands.device

import app.aaps.pump.apex.interfaces.ApexDeviceInfo
import app.aaps.pump.apex.utils.asShortAsByteArray

/** Set dual wave bolus.
 *
 * * [firstDose] - First bolus dose in 0.025U steps
 * * [secondDose] - First bolus dose in 0.025U steps
 * * [interval] - Interval in 15 minute steps
 */
class DualBolus(
    info: ApexDeviceInfo,
    val firstDose: Int,
    val secondDose: Int,
    val interval: Int,
) : BaseValueCommand(info) {
    override val valueId = 0x14
    override val isWrite = true

    override val additionalData: ByteArray
        get() = firstDose.asShortAsByteArray() + secondDose.asShortAsByteArray() + interval.asShortAsByteArray()

    override fun toString(): String = "DualBolus(first = $firstDose, second = $secondDose, interval = $interval)"
}
