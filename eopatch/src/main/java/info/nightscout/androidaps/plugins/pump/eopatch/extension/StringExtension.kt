package info.nightscout.androidaps.plugins.pump.eopatch.extension

import android.text.Html
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@Throws(JSONException::class)
fun String.toJson(): String =
        when (get(0)) {
            '{' -> JSONObject(this).toString(4)
            '[' -> JSONArray(this).toString(4)
            else -> ""
        }

fun String.fromHtml(): CharSequence = Html.fromHtml(this, Html.FROM_HTML_MODE_COMPACT)

fun String.isEmpty(): Boolean{
    return this.length == 0
}

fun String.getSeparatorForLog(): String {
    return StringBuilder().let {
        for (i in 0 until length) {
            it.append("=")
        }
        it.toString()
    }
}

fun String.convertUtcToLocalDate(): Date {
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
    val convertDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val timeZone = TimeZone.getDefault()

    var parseDate = format.parse(this)

    val convertedDate = convertDateFormat.format(parseDate)
    parseDate = convertDateFormat.parse(convertedDate)

    val locTime = convertDateFormat.format(parseDate.time + timeZone.getOffset(parseDate.time)).replace("+0000", "")

    val retDate = convertDateFormat.parse(locTime)

    return retDate
}