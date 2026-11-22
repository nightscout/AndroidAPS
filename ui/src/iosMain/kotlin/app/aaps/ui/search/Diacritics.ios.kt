package app.aaps.ui.search

import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSDiacriticInsensitiveSearch
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.stringByFoldingWithOptions

/**
 * Foundation folds diacritics away in one call, which is the same idea as the NFD-and-strip the
 * Android side does.
 *
 * Folding is locale sensitive, so a locale is not passed: the default rules are wanted here, not the
 * user's. With a locale, Turkish would fold "I" to a dotless "ı" and a search for "IOB" would stop
 * matching for Turkish users only.
 *
 * `NSString.create(string = this)` and not `this as NSString`: the cast works at run time, but the
 * compiler warns that it "can never succeed". `DiacriticsTest` runs this on `iosSimulatorArm64`.
 */
@OptIn(BetaInteropApi::class)
internal actual fun String.removeDiacritics(): String =
    NSString.create(string = this).stringByFoldingWithOptions(NSDiacriticInsensitiveSearch, null)
