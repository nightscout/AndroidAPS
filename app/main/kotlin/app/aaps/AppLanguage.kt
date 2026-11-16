package app.aaps

import app.aaps.core.interfaces.resources.LanguageTag

/**
 * The language Android should show AAPS in, as a BCP 47 tag, or null for the phone's language.
 *
 * [stored] is the AAPS language setting (`StringKey.GeneralLanguage`), with its old values fixed up by
 * [LanguageTag]. Simple mode always follows the phone.
 */
internal fun appLanguageTag(stored: String, simpleMode: Boolean): String? =
    if (simpleMode) null else LanguageTag.of(stored)
