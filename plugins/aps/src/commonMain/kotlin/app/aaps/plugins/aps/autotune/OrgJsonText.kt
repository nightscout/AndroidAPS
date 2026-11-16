package app.aaps.plugins.aps.autotune

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Writes JSON text exactly as Android's `org.json` wrote it, for the files of an Autotune run.
 *
 * The files are read by people and by oref0, to compare a run here with one on a computer, so moving
 * Autotune off `org.json` must not change their text. kotlinx writes a different text for the same
 * values, so this writer copies `JSONObject.toString(indent)`:
 *
 * - a whole double is written without its fraction: `8`, not `8.0`, and negative zero as `-0`
 * - `/` is escaped as `\/`, and a control character as `\u00xx` in lower case
 * - with an indent, each value is on its own line and a key is followed by `": "`; without one, the
 *   text has no space at all
 * - an empty object or array is `{}` or `[]` at any indent
 *
 * A key with a null value was left out by `org.json`, so a caller must not put one. A number that is
 * not finite was refused by `org.json`; [render] refuses it too, with [NonFiniteNumberException],
 * where the old code got a `JSONException`.
 */
internal object OrgJsonText {

    fun render(element: JsonElement, indent: Int = 0): String = StringBuilder().also { write(it, element, indent, 0) }.toString()

    private fun write(out: StringBuilder, element: JsonElement, indent: Int, depth: Int) {
        when (element) {
            is JsonNull      -> out.append("null")
            is JsonPrimitive -> if (element.isString) string(out, element.content) else out.append(number(element.content))
            is JsonArray     ->
                if (element.isEmpty()) out.append("[]")
                else {
                    out.append('[')
                    element.forEachIndexed { index, item ->
                        if (index > 0) out.append(',')
                        newLine(out, indent, depth + 1)
                        write(out, item, indent, depth + 1)
                    }
                    newLine(out, indent, depth)
                    out.append(']')
                }

            is JsonObject    ->
                if (element.isEmpty()) out.append("{}")
                else {
                    out.append('{')
                    var first = true
                    for ((key, value) in element) {
                        if (!first) out.append(',')
                        first = false
                        newLine(out, indent, depth + 1)
                        string(out, key)
                        out.append(if (indent == 0) ":" else ": ")
                        write(out, value, indent, depth + 1)
                    }
                    newLine(out, indent, depth)
                    out.append('}')
                }
        }
    }

    private fun newLine(out: StringBuilder, indent: Int, depth: Int) {
        if (indent == 0) return
        out.append('\n')
        repeat(indent * depth) { out.append(' ') }
    }

    /**
     * The text of a number or of `true`/`false`, as `JSONObject.numberToString` gives it.
     *
     * kotlinx keeps the text a primitive was made from: `10` for an Int or a Long, `10.0` for a
     * Double. Only a text with a fraction or an exponent came from a double, so only that one is
     * written again.
     */
    private fun number(content: String): String {
        if (content == "true" || content == "false") return content
        if (content.none { it == '.' || it == 'e' || it == 'E' } && content != "NaN" && !content.endsWith("Infinity")) return content
        val value = content.toDouble()
        if (!value.isFinite()) throw NonFiniteNumberException(content)
        if (value == 0.0 && 1.0 / value < 0) return "-0"
        val whole = value.toLong()
        return if (value == whole.toDouble()) whole.toString() else content
    }

    private fun string(out: StringBuilder, text: String) {
        out.append('"')
        for (c in text) {
            when (c) {
                '"', '\\', '/' -> out.append('\\').append(c)
                '\t'           -> out.append("\\t")
                '\b'           -> out.append("\\b")
                '\n'           -> out.append("\\n")
                '\r'           -> out.append("\\r")
                '\u000C'       -> out.append("\\f")
                else           ->
                    if (c.code <= 0x1F) out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                    else out.append(c)
            }
        }
        out.append('"')
    }
}

/** A NaN or an infinite number, which JSON cannot hold. */
internal class NonFiniteNumberException(value: String) : IllegalArgumentException("JSON cannot hold the number $value")

/**
 * [value] when it is finite. `org.json` refused NaN and infinity at `put`, so a builder that kept the
 * values put before the bad one checks each value with this, at the same place.
 */
internal fun finite(value: Double): Double = if (value.isFinite()) value else throw NonFiniteNumberException(value.toString())
