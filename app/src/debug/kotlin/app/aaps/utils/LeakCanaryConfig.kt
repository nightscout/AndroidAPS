package app.aaps.utils

import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import leakcanary.LeakCanary

/**
 * Configures LeakCanary with optional Firebase Crashlytics reporting.
 *
 * @param isEnabled Whether to enable heap dumping and the launcher icon. A function, so that the
 *                  release build, which has no LeakCanary, never runs it: it reads a marker file
 *                  through SAF, which is slow.
 * @param fabricPrivacy FabricPrivacy instance for uploading memory leaks to Firebase Crashlytics.
 *                      When provided, memory leaks will be uploaded via FabricPrivacy.logException.
 */
fun configureLeakCanary(isEnabled: () -> Boolean = { false }, fabricPrivacy: FabricPrivacy? = null) {
    val enabled = isEnabled()
    val eventListeners = if (fabricPrivacy != null) {
        LeakCanary.config.eventListeners + LeakUploadService(fabricPrivacy)
    } else {
        LeakCanary.config.eventListeners
    }

    LeakCanary.config = LeakCanary.config.copy(
        dumpHeap = enabled,
        eventListeners = eventListeners
    )
    LeakCanary.showLeakDisplayActivityLauncherIcon(enabled)
}
