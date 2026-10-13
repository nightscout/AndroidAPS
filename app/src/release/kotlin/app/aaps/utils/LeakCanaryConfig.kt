package app.aaps.utils

import app.aaps.core.interfaces.utils.fabric.FabricPrivacy

/**
 * This method is added just to ensure we can build the application in release mode.
 * LeakCanary is not included in release builds.
 */
@Suppress("UNUSED_PARAMETER")
fun configureLeakCanary(isEnabled: Boolean = false, fabricPrivacy: FabricPrivacy? = null) {
    // do nothing - LeakCanary is not available in release builds
}
