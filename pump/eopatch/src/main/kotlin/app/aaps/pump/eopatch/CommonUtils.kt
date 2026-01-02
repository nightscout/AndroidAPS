package app.aaps.pump.eopatch

import io.reactivex.rxjava3.disposables.Disposable
import java.util.*
import kotlin.math.abs
import kotlin.math.min

object CommonUtils {

    fun dispose(vararg disposable: Disposable?) {
        for (d in disposable) {
            d?.let {
                if (!it.isDisposed) {
                    it.dispose()
                }
            }
        }
    }

    fun hasText(str: CharSequence?): Boolean {
        if (str == null || str.isEmpty()) {
            return false
        }
        val strLen = str.length
        for (i in 0 until strLen) {
            if (!Character.isWhitespace(str[i])) {
                return true
            }
        }
        return false
    }

    fun hasText(str: String?): Boolean {
        return str?.let { hasText(it as CharSequence) } == true
    }

    fun isStringEmpty(cs: CharSequence?): Boolean {
        return cs == null || cs.isEmpty()
    }

    @JvmStatic fun dateString(millis: Long): String {
        if (millis == 0L) return ""

        val c = Calendar.getInstance()
        c.timeInMillis = millis
        return dateString(c)
    }

    fun dateString(c: Calendar): String {
        return String.format(
            Locale.US, "%04d-%02d-%02d %02d:%02d:%02d",
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY),
            c.get(Calendar.MINUTE),
            c.get(Calendar.SECOND)
        )
    }

    fun getRemainHourMin(timeMillis: Long): Pair<Long, Long> {
        val diffHours: Long
        var diffMinutes: Long

        if (timeMillis >= 0) {
            diffMinutes = abs(timeMillis / (60 * 1000) % 60) + 1
            if (diffMinutes == 60L) {
                diffMinutes = 0
                diffHours = abs(timeMillis / (60 * 60 * 1000)) + 1
            } else {
                diffHours = abs(timeMillis / (60 * 60 * 1000))
            }
        } else {
            diffMinutes = abs(timeMillis / (60 * 1000) % 60)
            diffHours = abs(timeMillis / (60 * 60 * 1000))
        }
        return Pair(diffHours, diffMinutes)
    }

    fun nearlyEqual(a: Float, b: Float, epsilon: Float): Boolean {
        val absA = abs(a)
        val absB = abs(b)
        val diff = abs(a - b)
        return if (a == b) {
            true
        } else if (a == 0f || b == 0f || absA + absB < java.lang.Float.MIN_NORMAL || absA + absB < 1.0f) {
            // For values at or near zero, or small values (sum < 1.0), use absolute difference
            diff < epsilon
        } else {
            // For other values, use relative difference
            diff / min(absA + absB, Float.MAX_VALUE) < epsilon
        }
    }

    fun <T : Any> clone(src: T): T {
        return GsonHelper.sharedGson().fromJson(GsonHelper.sharedGson().toJson(src), src.javaClass)
    }
}
