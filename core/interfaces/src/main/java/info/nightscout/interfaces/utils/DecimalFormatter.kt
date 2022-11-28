package info.nightscout.interfaces.utils

import info.nightscout.interfaces.R
import info.nightscout.interfaces.pump.Pump
import info.nightscout.shared.interfaces.ResourceHelper
import java.text.DecimalFormat

object DecimalFormatter {

    private val format0dec = DecimalFormat("0")
    private val format1dec = DecimalFormat("0.0")
    private val format2dec = DecimalFormat("0.00")
    private val format3dec = DecimalFormat("0.000")

    fun to0Decimal(value: Double): String = format0dec.format(value)
    fun to0Decimal(value: Double, unit: String): String = format0dec.format(value) + unit
    fun to1Decimal(value: Double): String = format1dec.format(value)
    fun to1Decimal(value: Double, unit: String): String = format1dec.format(value) + unit
    fun to2Decimal(value: Double): String = format2dec.format(value)
    fun to2Decimal(value: Double, unit: String): String = format2dec.format(value) + unit
    fun to3Decimal(value: Double): String = format3dec.format(value)
    fun to3Decimal(value: Double, unit: String): String = format3dec.format(value) + unit
    fun toPumpSupportedBolus(value: Double, pump: Pump): String = if (pump.pumpDescription.bolusStep <= 0.051) to2Decimal(value) else to1Decimal(value)
    fun toPumpSupportedBolus(value: Double, pump: Pump, rh: ResourceHelper): String =
        if (pump.pumpDescription.bolusStep <= 0.051) rh.gs(R.string.format_insulin_units, value)
        else rh.gs(R.string.format_insulin_units1, value)

    fun pumpSupportedBolusFormat(pump: Pump): DecimalFormat = if (pump.pumpDescription.bolusStep <= 0.051) DecimalFormat("0.00") else DecimalFormat("0.0")
}