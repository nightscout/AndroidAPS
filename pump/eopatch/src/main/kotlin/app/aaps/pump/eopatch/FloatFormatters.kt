package app.aaps.pump.eopatch

import java.util.*
import java.util.function.Function

object FloatFormatters {

    val INSULIN = Function<Number, String> { value -> String.format(Locale.US, "%.2f", value.toFloat()) }
    val FAT = Function<Number, String> { value -> String.format(Locale.US, "%.1f", value.toFloat()) }
    val DURATION = Function<Number, String> { value -> String.format(Locale.US, "%.1f", value.toFloat()) }

    fun insulin(value: Float): String {
        return INSULIN.apply(value)
    }

    fun insulin(value: Float, suffix: String?): String {
        return if (CommonUtils.isStringEmpty(suffix)) {
            INSULIN.apply(value)
        } else {
            INSULIN.apply(value) + " " + suffix!!
        }
    }

    fun duration(value: Float): String {
        return DURATION.apply(value)
    }

    fun duration(value: Float, suffix: String?): String {
        return if (CommonUtils.isStringEmpty(suffix)) {
            DURATION.apply(value)
        } else {
            DURATION.apply(value) + " " + suffix!!
        }
    }
}
