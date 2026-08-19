package app.aaps.core.utils.concurrent

import platform.Foundation.NSRecursiveLock

/**
 * iOS: `NSRecursiveLock` rather than `NSLock`, because a monitor is reentrant and the call sites
 * being ported rely on that.
 */
actual class AapsLock actual constructor() {

    private val lock = NSRecursiveLock()

    actual fun <T> withLock(action: () -> T): T {
        lock.lock()
        try {
            return action()
        } finally {
            lock.unlock()
        }
    }
}
