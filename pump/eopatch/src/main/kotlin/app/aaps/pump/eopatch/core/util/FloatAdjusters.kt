package app.aaps.pump.eopatch.core.util

import app.aaps.pump.eopatch.core.ble.AppConstant
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.floor

object FloatAdjusters {

    private val INSULIN_STEP = AppConstant.INSULIN_UNIT_P

    val CEIL2_BASAL_RATE: (Float) -> Float = { ceilToStep(it) }
    val FLOOR2_INSULIN: (Float) -> Float = { floorToStep(it) }
    val FLOOR2_BOLUS: (Float) -> Float = { floorToStep(it) }
    val ROUND2_INSULIN: (Float) -> Float = { roundToStep(it) }
    val ROUND2_BASAL_RATE: (Float) -> Float = { roundToStep(it) }
    val FLOOR2_BASAL_RATE: (Float) -> Float = { floorToStep(it) }

    private fun preAdjust(f: Float): BigDecimal =
        BigDecimal(f.toString())
            .setScale(4, RoundingMode.HALF_EVEN)
            .setScale(3, RoundingMode.FLOOR)

    private fun floorToDecimal(f: Float, decimalPlaceVar: Float): Float {
        val preNum = preAdjust(f)
        val postNum = BigDecimal(decimalPlaceVar.toString())
        return (floor((preNum.multiply(postNum)).toFloat().toDouble()) / decimalPlaceVar).toFloat()
    }

    private fun ceilToStep(f: Float, step: Float = INSULIN_STEP): Float {
        val retVal = floorToDecimal(f, 1000f)
        val valueDecimal = BigDecimal(retVal.toString()).setScale(3, RoundingMode.DOWN)
        val scaleDecimal = BigDecimal((1 / step).toString())
        val adjustVal = valueDecimal.multiply(scaleDecimal)
            .setScale(0, RoundingMode.CEILING)
            .divide(scaleDecimal).toFloat()
        return floorToDecimal(adjustVal, AppConstant.INSULIN_DECIMAL_PLACE_VAR)
    }

    private fun floorToStep(f: Float, step: Float = INSULIN_STEP): Float {
        val retVal = floorToDecimal(f, 1000f)
        val valueDecimal = BigDecimal(retVal.toString()).setScale(3, RoundingMode.DOWN)
        val scaleDecimal = BigDecimal((1 / step).toString())
        val adjustVal = valueDecimal.multiply(scaleDecimal)
            .setScale(0, RoundingMode.FLOOR)
            .divide(scaleDecimal).toFloat()
        return floorToDecimal(adjustVal, AppConstant.INSULIN_DECIMAL_PLACE_VAR)
    }

    private fun roundToStep(f: Float, step: Float = INSULIN_STEP): Float {
        val retVal = floorToDecimal(f, 1000f)
        val valueDecimal = BigDecimal(retVal.toString()).setScale(3, RoundingMode.DOWN)
        val scaleDecimal = BigDecimal((1 / step).toString())
        val adjustVal = valueDecimal.multiply(scaleDecimal)
            .setScale(0, RoundingMode.HALF_UP)
            .divide(scaleDecimal).toFloat()
        return floorToDecimal(adjustVal, AppConstant.INSULIN_DECIMAL_PLACE_VAR)
    }
}
