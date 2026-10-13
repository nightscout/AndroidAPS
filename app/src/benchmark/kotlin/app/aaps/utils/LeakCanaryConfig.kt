package app.aaps.utils

import app.aaps.core.interfaces.utils.fabric.FabricPrivacy

@Suppress("UNUSED_PARAMETER")
fun configureLeakCanary(
    isEnabled: Boolean = false,
    fabricPrivacy: FabricPrivacy? = null
) {
    // no-op for benchmark variant
}
