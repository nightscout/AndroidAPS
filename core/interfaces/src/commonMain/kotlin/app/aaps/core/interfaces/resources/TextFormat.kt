package app.aaps.core.interfaces.resources

import app.aaps.core.data.format.NumberFormat

/**
 * Substitutes arguments into an Android format string, for the platforms that have no
 * `Resources.getString(id, args)` to do it for them.
 *
 * Android hands the string and the arguments to `java.util.Formatter`. iOS and Kotlin/Native have no
 * such thing in common code - `String.format` is JVM only - so the subset the app actually uses is
 * implemented here: `%s`, `%d`, `%f`, their uppercase forms, an explicit argument index (`%1$s`), a
 * precision (`%.1f`), a width, and `%%` for a literal per cent sign.
 *
 * Two decisions worth knowing about:
 *
 * - **Numbers go through [NumberFormat]**, the same type the rest of the app formats with, so a
 *   decimal separator here matches one produced anywhere else on the same device. Writing a private
 *   rounding routine would have been a second answer to a question already answered.
 * - **An argument that is not there leaves the placeholder visible.** Java throws
 *   `MissingFormatArgumentException`, which on a phone means a crash while showing a label. A visible
 *   `%2$s` in the middle of a sentence is a bug report someone can act on and not a lost session.
 */
fun formatTemplate(template: String, args: List<Any?>): String {
    if (args.isEmpty() || !template.contains('%')) return template
    var next = 0
    return SPEC.replace(template) { match ->
        val conversion = match.groupValues[5]
        if (conversion == "%") return@replace "%"

        val explicit = match.groupValues[1].toIntOrNull()
        val index = if (explicit != null) explicit - 1 else next++
        if (index !in args.indices) return@replace match.value

        val flags = match.groupValues[2]
        val width = match.groupValues[3].toIntOrNull() ?: 0
        val precision = match.groupValues[4].toIntOrNull()
        val text = render(args[index], conversion, precision)
        pad(text, width, leftAlign = flags.contains('-'), zeroFill = flags.contains('0'))
    }
}

private fun render(arg: Any?, conversion: String, precision: Int?): String {
    val text = when (conversion.first().lowercaseChar()) {
        'd'  -> (arg as? Number)?.toLong()?.toString() ?: arg.toString()
        'f'  -> {
            // Java's default for %f is six decimals, and a caller that wanted fewer said so.
            val digits = precision ?: 6
            (arg as? Number)
                ?.let { NumberFormat(minFractionDigits = digits, maxFractionDigits = digits).format(it.toDouble()) }
                ?: arg.toString()
        }

        else -> arg?.toString() ?: "null"
    }
    return if (conversion.first().isUpperCase()) text.uppercase() else text
}

private fun pad(text: String, width: Int, leftAlign: Boolean, zeroFill: Boolean): String {
    if (text.length >= width) return text
    val fill = width - text.length
    return when {
        leftAlign -> text + " ".repeat(fill)
        // Zero fill goes after any sign, so -1 padded to four is "-001" and not "00-1".
        zeroFill && (text.startsWith('-') || text.startsWith('+')) ->
            text.first() + "0".repeat(fill) + text.drop(1)

        zeroFill  -> "0".repeat(fill) + text
        else      -> " ".repeat(fill) + text
    }
}

/** `%`, optional `index$`, optional flags, optional width, optional `.precision`, conversion. */
private val SPEC = Regex("""%(?:(\d+)\$)?([-+ 0#,]*)(\d+)?(?:\.(\d+))?([a-zA-Z%])""")
