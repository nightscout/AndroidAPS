package info.nightscout.androidaps.plugins.pump.medtronic.data.dto

import com.google.gson.annotations.Expose
import info.nightscout.androidaps.plugins.pump.medtronic.defs.PumpBolusType
import info.nightscout.pump.core.utils.StringUtil

/**
 * Application: GGC - GNU Gluco Control
 * Plug-in: Pump Tool (support for Pump devices)
 *
 *
 * See AUTHORS for copyright information.
 *
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free
 * Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 *
 * Filename: BolusDTO Description: Bolus DTO
 *
 *
 * Author: Andy {andy@atech-software.com}
 */
class BolusDTO constructor(atechDateTime: Long,
                           @Expose var requestedAmount: Double,
                           @Expose var deliveredAmount: Double,
                           @Expose var duration: Int = 0
) : PumpTimeStampedRecord(atechDateTime) {

    @Expose
    var immediateAmount: Double? = null // when Multiwave this is used

    @Expose
    lateinit var bolusType: PumpBolusType

    var insulinOnBoard: Double? = null

    private val durationString: String
        get() {
            var minutes = duration
            val h = minutes / 60
            minutes -= h * 60
            return StringUtil.getLeadingZero(h, 2) + ":" + StringUtil.getLeadingZero(minutes, 2)
        }

    val value: String
        get() = if (bolusType === PumpBolusType.Normal || bolusType === PumpBolusType.Audio) {
            getFormattedDecimal(deliveredAmount)
        } else if (bolusType === PumpBolusType.Extended) {
            String.format("AMOUNT_SQUARE=%s;DURATION=%s", getFormattedDecimal(deliveredAmount),
                durationString)
        } else {
            String.format("AMOUNT=%s;AMOUNT_SQUARE=%s;DURATION=%s", getFormattedDecimal(immediateAmount!!),
                getFormattedDecimal(deliveredAmount), durationString)
        }

    val displayableValue: String
        get() {
            var valueTemp = value
            valueTemp = valueTemp.replace("AMOUNT_SQUARE=", "Amount Square: ")
            valueTemp = valueTemp.replace("AMOUNT=", "Amount: ")
            valueTemp = valueTemp.replace("DURATION=", "Duration: ")
            return valueTemp
        }

    override fun getFormattedDecimal(value: Double): String {
        return StringUtil.getFormattedValueUS(value, 2)
    }

    val bolusKey: String
        get() = "Bolus_" + bolusType.name

    override fun toString(): String {
        return "BolusDTO [type=" + bolusType.name + ", " + value + "]"
    }
}