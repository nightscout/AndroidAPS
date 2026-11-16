package app.aaps.pump.aaruy.common.util

import android.os.Build
import androidx.annotation.RequiresApi
import com.aaruy.mylibrary.AALogger
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

object AaruySetting {
    // val baseTimestamp = 1609430400000
    val precisionTimes: Double = 1000.0  //精度的实际值的倍数
    const val glucoseUnitTimes: Int = 18//mmol/L与mg/dL的换算倍数，1mmol/L = 18mg/dL
    const val daySeconds: Long = 86400//单位秒，一天多少时间
    const val precision: Long = 25 //精度

    fun factInjectValue(value: Double): Double {
        return AaruyNumberUtils.division(value, precisionTimes, 3)
    }
}