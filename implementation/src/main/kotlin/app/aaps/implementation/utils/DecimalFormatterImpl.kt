package app.aaps.implementation.utils

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DecimalFormatter
import dagger.Reusable
import java.text.DecimalFormat
import javax.inject.Inject

@Reusable
class DecimalFormatterImpl @Inject constructor(
    private val rh: ResourceHelper
) : DecimalFormatter {

    private val format0dec = DecimalFormat("0")
    private val format1dec = DecimalFormat("0.0")
    private val format2dec = DecimalFormat("0.00")
    private val format3dec = DecimalFormat("0.000")

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

    override fun pumpSupportedBolusFormat(bolusStep: Double): DecimalFormat = if (bolusStep <= 0.051) DecimalFormat("0.00") else DecimalFormat("0.0")
}