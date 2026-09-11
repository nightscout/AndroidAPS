package app.aaps.pump.eopatch.core.ble

import kotlin.math.roundToInt

object PumpCounter {

    fun getPumpCount(dose: Float): Int = getPumpCountBase(dose)

    fun getPumpCountShort(dose: Float): Short {
        val count = getPumpCountBase(dose)
        return if (count > Short.MAX_VALUE) Short.MAX_VALUE else count.toShort()
    }

    private fun getPumpCountBase(dose: Float): Int =
        if (dose <= 0f) 0 else (dose / AppConstant.INSULIN_UNIT_P).roundToInt()
}
