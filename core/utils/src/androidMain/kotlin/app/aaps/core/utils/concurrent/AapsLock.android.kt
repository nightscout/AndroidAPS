package app.aaps.core.utils.concurrent

/**
 * Android/JVM: a plain monitor, so this is exactly what the call sites did before they were made
 * multiplatform. Monitors are reentrant, which is the property [AapsLock] promises.
 */
actual class AapsLock actual constructor() {

    private val monitor = Any()

    actual fun <T> withLock(action: () -> T): T = synchronized(monitor) { action() }
}
