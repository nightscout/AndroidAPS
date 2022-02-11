package info.nightscout.androidaps.plugins.pump.eopatch


import android.content.Context
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil
import io.reactivex.disposables.Disposable
import java.util.*
import java.util.function.Function

object CommonUtils {
    val TO_INT = Function<Number, Int> { it.toInt() }
    val TO_FLOAT = Function<Number, Float> { it.toFloat() }
    val TO_STRING = Function<Number, String> { it.toString() }
    val TO_CLOCK = Function<Number, String>{ num -> String.format(Locale.US, "%d:%02d", num.toInt() / 60, num.toInt() % 60) }

    @JvmStatic fun dispose(vararg disposable: Disposable?) {
        for (d in disposable){
            d?.let {
                if (!it.isDisposed()) {
                    it.dispose()
                }
            }
        }
    }

    @JvmStatic fun nullSafe(ch: CharSequence?): String {
        if (ch == null)
            return ""
        val str = ch.toString()
        return str
    }

    @JvmStatic fun hasText(str: CharSequence?): Boolean {
        if (str == null ||  str.length == 0) {
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

    @JvmStatic fun hasText(str: String?): Boolean {
        return str?.let{hasText(it as CharSequence)}?:false
    }

    @JvmStatic fun isStringEmpty(cs: CharSequence?): Boolean {
        return cs == null || cs.length == 0
    }

    @JvmStatic fun dateString(millis: Long): String {
        if(millis == 0L) return ""

        val c = Calendar.getInstance()
        c.timeInMillis = millis
        return dateString(c)
    }

    fun dateString(c: Calendar): String {
        return String.format(Locale.US, "%04d-%02d-%02d %02d:%02d:%02d",
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY),
            c.get(Calendar.MINUTE),
            c.get(Calendar.SECOND))
    }

    fun getTimeString(millis: Long): String {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        return getTimeString(c)
    }

    fun getTimeString(c: Calendar): String {
        return String.format(Locale.US, "%02d:%02d",
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    fun bytesToStringArray(byteArray: ByteArray?): String {
        if (byteArray == null || byteArray.size == 0) {
            return "null"
        }

        val sb = StringBuilder()
        for (b in byteArray) {
            if (sb.length > 0) {
                sb.append(String.format(" %02x", b))
            } else {
                sb.append(String.format("0x%02x", b))
            }
        }
        return sb.toString()
    }

    fun insulinFormat(): String {
        if (AppConstant.INSULIN_UNIT_STEP_U == 0.1f) {
            return "%.1f"
        }
        return if (AppConstant.INSULIN_UNIT_STEP_U == 0.05f) {
            "%.2f"
        } else "%.2f 인슐린출력형식추가하셈"

    }

    fun convertInsulinFormat(context: Context, strResId: Int): String {
        if (AppConstant.INSULIN_UNIT_STEP_U == 0.1f) {
            return context.getString(strResId).replace(".2f", ".1f")
        }
        if (AppConstant.INSULIN_UNIT_STEP_U == 0.05f) {
            return context.getString(strResId)
        }

        return context.getString(strResId).replace(".2f", ".2f 인슐린출력형식추가하셈")

    }

    fun getRemainHourMin(timeMillis: Long): Pair<Long, Long> {
        val diffHours: Long
        var diffMinutes: Long

        if (timeMillis >= 0) {
            diffMinutes = Math.abs(timeMillis / (60 * 1000) % 60) + 1
            if (diffMinutes == 60L) {
                diffMinutes = 0
                diffHours = Math.abs(timeMillis / (60 * 60 * 1000)) + 1
            } else {
                diffHours = Math.abs(timeMillis / (60 * 60 * 1000))
            }
        } else {
            diffMinutes = Math.abs(timeMillis / (60 * 1000) % 60)
            diffHours = Math.abs(timeMillis / (60 * 60 * 1000))
        }
        return Pair(diffHours, diffMinutes)
    }

    fun getTimeString(minutes: Int): String {
        return String.format("%d:%02d", minutes / 60, minutes % 60)
    }

    fun getTimeString_hhmm(minutes: Int): String {
        return String.format("%02d:%02d", minutes / 60, minutes % 60)
    }

    @JvmStatic
    fun generatePumpId(date: Long, typeCode: Long = 0): Long {
        return DateTimeUtil.toATechDate(date) * 100L + typeCode
    }

    @JvmStatic
    fun nearlyEqual(a: Float, b: Float, epsilon: Float): Boolean {
        val absA = Math.abs(a)
        val absB = Math.abs(b)
        val diff = Math.abs(a - b)
        return if (a == b) { // shortcut, handles infinities
            true
        } else if (a == 0f || b == 0f || absA + absB < java.lang.Float.MIN_NORMAL) {
            // a or b is zero or both are extremely close to it
            // relative error is less meaningful here
            diff < epsilon * java.lang.Float.MIN_NORMAL
        } else { // use relative error
            diff / Math.min(absA + absB, Float.MAX_VALUE) < epsilon
        }
    }

    @JvmStatic
    fun nearlyNotEqual(a: Float, b: Float, epsilon: Float): Boolean {
        return !nearlyEqual(a, b, epsilon)
    }

    @JvmStatic
    fun <T : Any> clone(src: T): T {
        return GsonHelper.sharedGson().fromJson(GsonHelper.sharedGson().toJson(src), src.javaClass)
    }
}
