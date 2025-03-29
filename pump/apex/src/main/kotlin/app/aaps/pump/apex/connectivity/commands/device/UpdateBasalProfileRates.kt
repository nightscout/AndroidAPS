package app.aaps.pump.apex.connectivity.commands.device

import app.aaps.pump.apex.interfaces.ApexDeviceInfo
import app.aaps.pump.apex.utils.shortLSB
import app.aaps.pump.apex.utils.shortMSB

/** Set currently used basal profile rates.
 *
 * * [rates] - 48 basal rates, one per 30 minute, in 0.025U steps
 */
class UpdateBasalProfileRates(
    info: ApexDeviceInfo,
    val rates: List<Int>
) : BaseValueCommand(info) {
    override val valueId = 0x00
    override val isWrite = true

    override val additionalData: ByteArray
        get() = ByteArray(96) {
            val rate = rates[it / 2]
            if (it % 2 == 0) rate.shortLSB()
            else rate.shortMSB()
        }

    override fun toString(): String = "UpdateBasalProfileRates(${rates.joinToString(", ", "[", "]")})"
}