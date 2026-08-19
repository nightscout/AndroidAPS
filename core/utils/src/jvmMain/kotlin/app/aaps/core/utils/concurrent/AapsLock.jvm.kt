package app.aaps.core.utils.concurrent

/**
 * Plain JVM target: same monitor as Android. The two source sets are separate compilations, so the
 * one line is stated twice rather than shared through an intermediate source set that exists only
 * for this.
 */
actual class AapsLock actual constructor() {

    private val monitor = Any()

    actual fun <T> withLock(action: () -> T): T = synchronized(monitor) { action() }
}
