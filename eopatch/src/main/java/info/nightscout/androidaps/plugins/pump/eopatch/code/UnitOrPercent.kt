package info.nightscout.androidaps.plugins.pump.eopatch.code

import com.google.android.gms.common.internal.Preconditions

enum class UnitOrPercent private constructor(val rawValue: Int, val symbol: String) {
    P(0, "%"),
    U(1, "U");


    fun isPercentage() = this == P
    fun isU() = this == U

    companion object {

        val U_PER_HOUR = "U/hr"
        val U_PER_DAY = "U/day"


        /**
         * rawValue로 값을 찾기, 못찾으면 null을 리턴
         */
        fun ofRaw(rawValue: Int?): UnitOrPercent? {
            if (rawValue == null) {
                return null
            }

            for (t in UnitOrPercent.values()) {
                if (t.rawValue == rawValue) {
                    return t
                }
            }
            return null
        }

        /**
         * rawValue로 값을 찾기, 못찾으면 defaultValue을 리턴
         */
        fun ofRaw(rawValue: Int?, defaultValue: UnitOrPercent): UnitOrPercent {
            Preconditions.checkNotNull(defaultValue)
            if (rawValue == null) {
                return defaultValue
            }

            for (t in UnitOrPercent.values()) {
                if (t.rawValue == rawValue) {
                    return t
                }
            }
            return defaultValue
        }
    }
}
