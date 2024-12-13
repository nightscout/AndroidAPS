package app.aaps.pump.eopatch.code

import java.util.concurrent.TimeUnit

@Suppress("unused")
enum class BolusExDuration(val index: Int, val minute: Int, val hour: Float) {

    OFF(0, 0, 0f),
    MINUTE_30(1, 30, 0.5f),
    MINUTE_60(2, 60, 1.0f),
    MINUTE_90(3, 90, 1.5f),
    MINUTE_120(4, 120, 2.0f),
    MINUTE_150(5, 150, 2.5f),
    MINUTE_180(6, 180, 3.0f),
    MINUTE_210(7, 210, 3.5f),
    MINUTE_240(8, 240, 4.0f),
    MINUTE_270(9, 270, 4.5f),
    MINUTE_300(10, 300, 5.0f),
    MINUTE_330(11, 330, 5.5f),
    MINUTE_360(12, 360, 6.0f),
    MINUTE_390(13, 390, 6.5f),
    MINUTE_420(14, 420, 7.0f),
    MINUTE_450(15, 450, 7.5f),
    MINUTE_480(16, 480, 8.0f);

    fun milli(): Long {
        return TimeUnit.MINUTES.toMillis(this.minute.toLong())
    }

    companion object {

        @JvmStatic
        fun ofRaw(rawValue: Int): BolusExDuration {
            for (t in BolusExDuration.entries) {
                if (t.minute == rawValue) {
                    return t
                }
            }
            return OFF
        }
    }

}
