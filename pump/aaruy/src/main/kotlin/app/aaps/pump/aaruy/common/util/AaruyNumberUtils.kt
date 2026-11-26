package app.aaps.pump.aaruy.common.util

import android.annotation.SuppressLint
import java.math.BigDecimal
import java.math.RoundingMode

object AaruyNumberUtils {

    @SuppressLint("DefaultLocale")
    fun getDotThreeDouble(num: Double): Double {
        val decimal = BigDecimal(num.toString())
        val value = decimal.setScale(3, RoundingMode.HALF_UP).toDouble()
        return String.format("%.3f", value).replace(",", ".").toDouble()
    }

    //忽略小数点后最后一位的0
    fun doubleConvertToString(value: Double): String {
        val doubleValue: Double = getDotThreeDouble(value)
        return doubleValue.toString() + ""
    }

    //获取double的后三位数并转换为字符串，小数点最后一位是0就忽略
    fun getDotThreeString(num: Double): String {
        var num = num
        try {
            num = getDotThreeDouble(num)
            return doubleConvertToString(num)
            //return String.format("%.3f",num);
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "0.000"
    }

    /**
     * 计算两个double类型相除后的保留scale位小数
     *
     * @param v1
     * @param v2
     * @param scale
     * @return
     */
    fun division(v1: Double, v2: Double, scale: Int): Double {
        require(scale >= 0) { "The scale must be a positive integer or zero" }
        require(v2 != 0.0) { "The v2 must not be zero" }
        val b1 = BigDecimal(v1.toString())
        val b2 = BigDecimal(v2.toString())
        return b1.divide(b2, scale, RoundingMode.HALF_UP).toDouble()
    }
}