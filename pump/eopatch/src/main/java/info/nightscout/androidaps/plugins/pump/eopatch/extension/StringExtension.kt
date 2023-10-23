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
