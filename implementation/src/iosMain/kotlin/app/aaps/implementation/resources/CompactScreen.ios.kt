package app.aaps.implementation.resources

import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPhone

/**
 * A phone is narrow, an iPad is not - the same split Android's `isTablet` resource makes.
 *
 * The idiom rather than the window width: an iPhone stays an iPhone whatever it is doing, while a
 * width read at start up would be wrong the moment an iPad entered split view. Android decides this
 * from a resource qualifier for the same reason.
 */
actual fun isCompactScreen(): Boolean =
    UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPhone
