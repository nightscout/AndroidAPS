package info.nightscout.androidaps.plugins.pump.eopatch.code

import com.google.android.gms.common.internal.Preconditions

enum class AlarmCategory private constructor(val rawValue: Int) {
    NONE(0),
    ALARM(1),
    ALERT(2);

    companion object {


        /**
         * rawValue로 값을 찾기, 못찾으면 null을 리턴
         */
        fun ofRaw(rawValue: Int?): AlarmCategory? {
            if (rawValue == null) {
                return null
            }

            for (t in AlarmCategory.values()) {
                if (t.rawValue == rawValue) {
                    return t
                }
            }
            return null
        }

        /**
         * rawValue로 값을 찾기, 못찾으면 defaultValue을 리턴
         */
        fun ofRaw(rawValue: Int?, defaultValue: AlarmCategory): AlarmCategory {
            Preconditions.checkNotNull(defaultValue)
            if (rawValue == null) {
                return defaultValue
            }

            for (t in AlarmCategory.values()) {
                if (t.rawValue == rawValue) {
                    return t
                }
            }
            return defaultValue
        }
    }
}
