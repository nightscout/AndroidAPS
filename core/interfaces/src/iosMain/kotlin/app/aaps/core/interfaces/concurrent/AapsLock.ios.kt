package app.aaps.core.interfaces.concurrent

import platform.Foundation.NSRecursiveLock

/**
 * iOS: `NSRecursiveLock` rather than `NSLock`, because a monitor is reentrant and the call sites
 * being ported rely on that.
 */
actual class AapsLock actual constructor() {

    private val delegate = NSRecursiveLock()

    actual fun lock() = delegate.lock()

    actual fun unlock() = delegate.unlock()
}
