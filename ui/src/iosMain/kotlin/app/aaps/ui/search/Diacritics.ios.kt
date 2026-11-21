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
// The compiler warns "This cast can never succeed", and it is wrong: casting between a Kotlin
// `String` and an `NSString` is the documented Kotlin/Native bridge between mapped types, and it
// converts at run time rather than throwing. `DiacriticsTest` proves it on `iosSimulatorArm64` -
// Czech and the Turkish capital-I case included - so do not "fix" this line.
@Suppress("CAST_NEVER_SUCCEEDS")
internal actual fun String.removeDiacritics(): String =
    (this as NSString).stringByFoldingWithOptions(NSDiacriticInsensitiveSearch, null)
