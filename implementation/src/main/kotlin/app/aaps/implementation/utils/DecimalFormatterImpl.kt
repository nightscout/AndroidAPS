package app.aaps.implementation.utils

import app.aaps.core.data.format.NumberFormat
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DecimalFormatter
import dagger.Reusable
import javax.inject.Inject

@Reusable
class DecimalFormatterImpl @Inject constructor(
    private val rh: ResourceHelper
) : DecimalFormatter {

    private val format0dec = NumberFormat.INTEGER
    private val format1dec = NumberFormat.DECIMAL_1
    private val format2dec = NumberFormat.DECIMAL_2
    private val format3dec = NumberFormat.DECIMAL_3

    override fun to0Decimal(value: Double): String = format0dec.format(value)
    override fun to0Decimal(value: Double, unit: String): String = format0dec.format(value) + unit
    override fun to1Decimal(value: Double): String = format1dec.format(value)
    override fun to1Decimal(value: Double, unit: String): String = format1dec.format(value) + unit
    override fun to2Decimal(value: Double): String = format2dec.format(value)
    override fun to2Decimal(value: Double, unit: String): String = format2dec.format(value) + unit
    override fun to3Decimal(value: Double): String = format3dec.format(value)
    override fun to3Decimal(value: Double, unit: String): String = format3dec.format(value) + unit
    override fun toPumpSupportedBolus(value: Double, bolusStep: Double): String = if (bolusStep <= 0.051) to2Decimal(value) else to1Decimal(value)
    override fun toPumpSupportedBolusWithUnits(value: Double, bolusStep: Double): String =
        if (bolusStep <= 0.051) rh.gs(app.aaps.core.ui.R.string.format_insulin_units, value) else rh.gs(app.aaps.core.ui.R.string.format_insulin_units1, value)
    override fun toPumpSupportedBolusWithUnits(value: PumpInsulin, bolusStep: Double): String =
        if (bolusStep <= 0.051) rh.gs(app.aaps.core.ui.R.string.pump_insulin_cu, value.cU) else rh.gs(app.aaps.core.ui.R.string.pump_insulin_cu1, value.cU)

    override fun pumpSupportedBolusFormat(bolusStep: Double): NumberFormat = if (bolusStep <= 0.051) NumberFormat.DECIMAL_2 else NumberFormat.DECIMAL_1
}