package app.aaps.core.interfaces.concurrent

import java.util.concurrent.locks.ReentrantLock

/**
 * Android/JVM: [ReentrantLock], which has the same reentrancy and blocking behaviour as the monitor
 * these call sites used before they were made multiplatform.
 *
 * A monitor would fit the semantics even better, but `synchronized` is a block construct - it cannot
 * be split into the separate lock/unlock calls the inline `withLock` needs.
 */
actual class AapsLock actual constructor() {

    private val delegate = ReentrantLock()

    actual fun lock() = delegate.lock()

    actual fun unlock() = delegate.unlock()
}
