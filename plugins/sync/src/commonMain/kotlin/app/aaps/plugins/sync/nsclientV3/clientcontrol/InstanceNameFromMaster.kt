package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences

/**
 * Uses the name this phone was added with on the master as its instance name, so the client wizard does not ask for
 * it again. Only while the phone has no instance name: one the user typed stays.
 */
internal fun Preferences.takeInstanceNameFromMaster(masterName: String?) {
    if (masterName == null || get(StringKey.GeneralInstanceName).isNotEmpty()) return
    val name = asciiInstanceName(masterName)
    if (name.isNotEmpty()) put(StringKey.GeneralInstanceName, name)
}

/**
 * [name] in the characters an instance name allows (`StringValidator.personName`): ASCII letters, digits, spaces, `'`
 * and `-`. Letters with marks lose them ("Miloš" is "Milos", "Straße" is "Strasse"); any other character becomes a
 * space. Empty when nothing is left, for example from a name in another script.
 */
internal fun asciiInstanceName(name: String): String =
    buildString {
        for (char in name) when {
            char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char == '\'' || char == '-' -> append(char)
            char in ASCII_LETTERS                                                                 -> append(ASCII_LETTERS.getValue(char))
            else                                                                                  -> append(' ')
        }
    }.split(' ').filter { it.isNotEmpty() }.joinToString(" ")

/** Latin letters with marks, and the few that are written as two letters in ASCII. */
private val ASCII_LETTERS: Map<Char, String> = buildMap {
    fun letters(withMarks: String, ascii: String) = withMarks.forEach { put(it, ascii) }
    letters("ÀÁÂÃÄÅĀĂĄ", "A"); letters("àáâãäåāăą", "a")
    letters("ÇĆĈĊČ", "C"); letters("çćĉċč", "c")
    letters("ĎĐÐ", "D"); letters("ďđð", "d")
    letters("ÈÉÊËĒĔĖĘĚ", "E"); letters("èéêëēĕėęě", "e")
    letters("ĜĞĠĢ", "G"); letters("ĝğġģ", "g")
    letters("ĤĦ", "H"); letters("ĥħ", "h")
    letters("ÌÍÎÏĨĪĬĮİ", "I"); letters("ìíîïĩīĭįı", "i")
    letters("Ĵ", "J"); letters("ĵ", "j")
    letters("Ķ", "K"); letters("ķ", "k")
    letters("ĹĻĽĿŁ", "L"); letters("ĺļľŀł", "l")
    letters("ÑŃŅŇ", "N"); letters("ñńņň", "n")
    letters("ÒÓÔÕÖØŌŎŐ", "O"); letters("òóôõöøōŏő", "o")
    letters("ŔŖŘ", "R"); letters("ŕŗř", "r")
    letters("ŚŜŞŠȘ", "S"); letters("śŝşšș", "s")
    letters("ŢŤŦȚ", "T"); letters("ţťŧț", "t")
    letters("ÙÚÛÜŨŪŬŮŰŲ", "U"); letters("ùúûüũūŭůűų", "u")
    letters("Ŵ", "W"); letters("ŵ", "w")
    letters("ÝŶŸ", "Y"); letters("ýÿŷ", "y")
    letters("ŹŻŽ", "Z"); letters("źżž", "z")
    letters("Æ", "AE"); letters("æ", "ae")
    letters("Œ", "OE"); letters("œ", "oe")
    letters("Þ", "Th"); letters("þ", "th")
    letters("ß", "ss")
}
