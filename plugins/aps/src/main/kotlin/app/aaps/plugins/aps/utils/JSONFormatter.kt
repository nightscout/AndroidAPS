package app.aaps.plugins.aps.utils

import android.text.Spanned
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.utils.HtmlHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JSONFormatter @Inject constructor(
    private val aapsLogger: AAPSLogger
) {

    fun format(jsonString: String?): Spanned {
        jsonString ?: return HtmlHelper.fromHtml("")
        val visitor = JsonVisitor(1, '\t')
        return try {
            when {
                jsonString == "undefined"                        -> HtmlHelper.fromHtml("undefined")
                jsonString.toByteArray()[0] == '['.code.toByte() -> HtmlHelper.fromHtml(visitor.visit(JSONArray(jsonString), 0))
                else                                             -> HtmlHelper.fromHtml(visitor.visit(JSONObject(jsonString), 0))
            }
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
            HtmlHelper.fromHtml("")
        }
    }

    fun format(jsonObject: JSONObject?): Spanned {
        jsonObject ?: return HtmlHelper.fromHtml("")
        val visitor = JsonVisitor(1, '\t')
        return try {
            HtmlHelper.fromHtml(visitor.visit(jsonObject, 0))
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
            HtmlHelper.fromHtml("")
        }
    }

    private class JsonVisitor(private val indentationSize: Int, private val indentationChar: Char) {

        fun visit(array: JSONArray, indent: Int): String {
            var ret = ""
            val length = array.length()
            if (length != 0) {
                ret += write("[", indent)
                for (i in 0 until length) {
                    ret += visit(array[i], indent)
                }
                ret += write("]", indent)
            }
            return ret
        }

        fun visit(obj: JSONObject, indent: Int): String {
            var ret = ""
            val length = obj.length()
            if (length != 0) {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    ret += write("<b>$key</b>: ", indent)
                    ret += visit(obj[key], indent + 1)
                    ret += "<br>"
                }
            }
            return ret
        }

        private fun visit(any: Any, indent: Int): String {
            var ret = ""
            val n: Long
            if (any is JSONArray) {
                ret += visit(any, indent)
            } else if (any is JSONObject) {
                ret += "<br>" + visit(any, indent)
            } else {
                if (any is String) {
                    ret += write("\"" + any.replace("<", "&lt;").replace(">", "&gt;") + "\"", indent)
                } else {
                    // try to detect Date as milliseconds
                    if (any is Long) {
                        n = any
                        ret += if (n in 1580000000001..1999999999999) { // from 2020.01.26 to 2033.05.18 it is with high probability a date object
                            val formatter: DateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            write(formatter.format(Date(n)), indent)
                        } else {
                            write(any.toString(), indent)
                        }
                    } else {
                        ret += write(any.toString(), indent)
                    }
                }
            }
            return ret
        }

        private fun write(data: String, indent: Int): String {
            var ret = ""
            for (i in 0 until indent * indentationSize) {
                ret += indentationChar
            }
            ret += data
            return ret
        }
    }
}