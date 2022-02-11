package info.nightscout.androidaps.plugins.pump.eopatch.code

enum class BgUnit private constructor(val rawValue: Int, val unitStr: String) {
    GRAM(1, "mg/dL"),
    MMOL(2, "mmol/L");

    fun isGram() = GRAM == this
    fun isMmol() = MMOL == this

    fun getUnit() = unitStr

    companion object {
        /**
         * rawValue로 값을 찾기, 못찾으면 null을 리턴
         */
        fun ofRaw(rawValue: Int?): BgUnit {
            for (t in BgUnit.values()) {
                if (t.rawValue == rawValue) {
                    return t
                }
            }
            return GRAM
        }

        /**
         * rawValue로 값을 찾기, 못찾으면 defaultValue을 리턴
         */
        fun ofRaw(rawValue: Int, defaultValue: BgUnit): BgUnit {
            for (t in BgUnit.values()) {
                if (t.rawValue == rawValue) {
                    return t
                }
            }
            return defaultValue
        }
    }
}
