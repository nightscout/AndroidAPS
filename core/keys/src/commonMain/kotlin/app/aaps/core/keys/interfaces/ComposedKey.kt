package app.aaps.core.keys.interfaces

interface ComposedKey {

    /**
     * Key is used as prefix for recognizing the preference
     *
     * Final key is composed as key + format, with the placeholders replaced by the arguments
     */
    val key: String

    /**
     * String used to format vararg.
     *
     * Only `%d` (whole number), `%s` (any value) and `%%` (a plain percent sign) are allowed.
     */
    val format: String

    /**
     * Compose final key from arguments.
     *
     * This does not use `String.format` on purpose, for two reasons:
     *
     * 1. The result becomes a preference key name. `String.format` follows a locale, and a locale
     *    with its own digits (Arabic, Nepali, Burmese, ...) would write `%d` in that script. The key
     *    would then be different and the stored setting would be lost. The old code passed
     *    `Locale.ENGLISH` to avoid that; building the text by hand removes the risk completely.
     * 2. `String.format` only exists on the JVM, so this file can be shared with other platforms.
     *
     * @throws IllegalArgumentException if the format holds anything but `%d`, `%s` and `%%`, if the
     *         value for a `%d` is not a whole number, or if the number of arguments does not match.
     */
    fun composeKey(vararg arguments: Any): String {
        val template = key + format
        val composed = StringBuilder(template.length + arguments.size * 4)
        var used = 0
        var i = 0
        while (i < template.length) {
            val char = template[i]
            if (char != '%') {
                composed.append(char)
                i++
                continue
            }
            require(i + 1 < template.length) { "Format of '$key' ends with a single %" }
            when (val type = template[i + 1]) {
                '%'  -> composed.append('%')

                's'  -> {
                    require(used < arguments.size) { "Format of '$key' needs more than the ${arguments.size} given arguments" }
                    composed.append(arguments[used])
                    used++
                }

                'd'  -> {
                    require(used < arguments.size) { "Format of '$key' needs more than the ${arguments.size} given arguments" }
                    val value = arguments[used]
                    require(value is Int || value is Long || value is Short || value is Byte) {
                        "Format of '$key' uses %d, so argument ${used + 1} must be a whole number, but it is ${value::class.simpleName}"
                    }
                    composed.append(value)
                    used++
                }

                else -> throw IllegalArgumentException("Format of '$key' uses %$type, only %d, %s and %% are supported")
            }
            i += 2
        }
        require(used == arguments.size) { "Format of '$key' uses $used arguments, but ${arguments.size} were given" }
        return composed.toString()
    }
}
