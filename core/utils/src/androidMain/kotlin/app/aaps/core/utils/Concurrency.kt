package app.aaps.core.utils

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "NOTHING_TO_INLINE")
inline fun Any.wait() = (this as Object).wait()

/**
 * Lock and wait a duration in milliseconds and nanos.
 * Unlike [java.lang.Object.wait] this interprets 0 as "don't wait" instead of "wait forever".
 */
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
fun Any.waitMillis(timeout: Long, nanos: Int = 0) {
    if (timeout > 0L || nanos > 0) {
        (this as Object).wait(timeout, nanos)
    }
}

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "NOTHING_TO_INLINE")
inline fun Any.notify() = (this as Object).notify()

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "NOTHING_TO_INLINE")
inline fun Any.notifyAll() = (this as Object).notifyAll()
