package info.nightscout.androidaps.plugins.pump.eopatch.code


import com.google.android.gms.common.internal.Preconditions
import com.google.gson.annotations.SerializedName

enum class AlarmSource private constructor(val rawValue: Int) {
    @SerializedName("0")
    NONE(0),
    @SerializedName("1")
    PATCH(1),
    @SerializedName("2")
    ADM(2),
    @SerializedName("3")
    CGM(3),
    @SerializedName("9")
    TEST(9);

    val isTest: Boolean
        get() = this == TEST

    val isCgm: Boolean
        get() = this == CGM

    companion object {

        /**
         * rawValue로 값을 찾기, 못찾으면 null을 리턴
         */
         @JvmStatic
        fun ofRaw(rawValue: Int?): AlarmSource {
            if (rawValue == null) {
                return NONE
            }

            for (t in AlarmSource.values()) {
                if (t.rawValue == rawValue) {
                    return t
                }
            }
            return NONE
        }

        @JvmStatic
        fun toRaw(source: AlarmSource?): Int {
            return if (source != null) source.rawValue else NONE.rawValue
        }

        /**
         * rawValue로 값을 찾기, 못찾으면 defaultValue을 리턴
         */
        fun ofRaw(rawValue: Int?, defaultValue: AlarmSource): AlarmSource {
            Preconditions.checkNotNull(defaultValue)
            if (rawValue == null) {
                return defaultValue
            }

            for (t in AlarmSource.values()) {
                if (t.rawValue == rawValue) {
                    return t
                }
            }
            return defaultValue
        }
    }
}
