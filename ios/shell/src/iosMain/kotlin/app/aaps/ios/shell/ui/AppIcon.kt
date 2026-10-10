package app.aaps.ios.shell.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

/**
 * The app's own icon, read out of the bundle it was built into.
 *
 * ## Why it is read rather than chosen
 *
 * Android picks a launcher icon per flavour in `IconsProviderImplementation`, and the desktop copy
 * of that rule reads [app.aaps.core.interfaces.configuration.Config]. iOS needs neither: the two app
 * targets, AAPSClient and AAPSClient2, already carry their own `AppIcon` asset, and one Kotlin
 * framework is linked into both. Asking the bundle therefore gives the icon of the app that is
 * actually running, and it cannot disagree with the icon on the home screen - which a second copy of
 * the artwork here eventually would.
 *
 * It also avoids shipping the owls twice. The bytes are already in the bundle.
 *
 * Null when the bundle will not give it up. The caller then draws the plain AAPS mark, which is
 * worse-looking but never wrong.
 */
internal fun loadAppIcon(): ImageBitmap? {
    val image = appIconNames().firstNotNullOfOrNull { UIImage.imageNamed(it) } ?: return null
    val png = UIImagePNGRepresentation(image) ?: return null
    return runCatching { png.toByteArray().decodeToImageBitmap() }.getOrNull()
}

/**
 * The names worth trying, best first.
 *
 * Xcode records the icon differently depending on how it was set. An asset catalog - what both
 * targets use - writes `CFBundleIconName`; loose files write `CFBundleIconFiles`, largest last.
 * "AppIcon" is the name both targets set as `ASSETCATALOG_COMPILER_APPICON_NAME`, so it is the
 * sensible last try if the plist says nothing.
 */
private fun appIconNames(): List<String> {
    val icons = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleIcons") as? Map<*, *>
    val primary = icons?.get("CFBundlePrimaryIcon") as? Map<*, *>
    val named = primary?.get("CFBundleIconName") as? String
    val files = (primary?.get("CFBundleIconFiles") as? List<*>).orEmpty().filterIsInstance<String>()
    return (listOfNotNull(named) + files.reversed() + "AppIcon").distinct()
}

/** Bytes out of an `NSData`. Compose decodes bytes on every platform, so nothing else is needed. */
@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).also { out -> out.usePinned { memcpy(it.addressOf(0), bytes, length) } }
}
