package app.aaps.ui.search

import platform.Foundation.NSDiacriticInsensitiveSearch
import platform.Foundation.NSString
import platform.Foundation.stringByFoldingWithOptions

/**
 * Foundation folds diacritics away in one call, which is the same idea as the NFD-and-strip the
 * Android side does.
 *
 * Folding is locale sensitive, so a locale is not passed: the default rules are wanted here, not the
 * user's. With a locale, Turkish would fold "I" to a dotless "ı" and a search for "IOB" would stop
 * matching for Turkish users only.
 */
internal actual fun String.removeDiacritics(): String =
    (this as NSString).stringByFoldingWithOptions(NSDiacriticInsensitiveSearch, null)
