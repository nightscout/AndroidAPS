package app.aaps.ui.search

/**
 * Returns this text with diacritics removed, so "průměr" matches a search for "prumer".
 *
 * Left to the platform on purpose. A hand written table would have to cover every language the app
 * is translated into, and would quietly stop matching for the ones it missed - Czech, Polish and
 * Turkish are the usual casualties. Both platforms already carry a full Unicode implementation.
 */
internal expect fun String.removeDiacritics(): String
