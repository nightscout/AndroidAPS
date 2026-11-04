package app.aaps.core.utils.receivers

/**
 * class contains useful String functions
 */
object StringUtils {

    fun removeSurroundingQuotes(input: String): String {
        var string = input
        if (string.length >= 2 && string[0] == '"' && string[string.length - 1] == '"') {
            string = string.substring(1, string.length - 1)
        }
        return string
    }
}