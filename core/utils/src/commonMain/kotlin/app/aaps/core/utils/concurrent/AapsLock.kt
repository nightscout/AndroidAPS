package app.aaps.core.utils.concurrent

/**
 * A mutual exclusion lock that works on every target.
 *
 * `kotlin.synchronized` and `@Synchronized` are JVM only, so code that guards shared state cannot be
 * multiplatform without something like this. The contract is deliberately the same as the monitor it
 * replaces:
 *
 * - **reentrant** - the thread holding the lock may take it again without deadlocking, which is what
 *   `synchronized` does and what the existing call sites assume
 * - **blocking** - [withLock] blocks the calling thread, it does not suspend. A `Mutex` from
 *   kotlinx-coroutines is NOT a drop-in replacement: it is not reentrant, and it needs a coroutine
 *
 * On Android the actual delegates straight to `kotlin.synchronized`, so the generated code and the
 * runtime behaviour there are unchanged - which matters, because the first user of this is the loop's
 * calculation cache.
 *
 * One lock guards one thing. Do NOT lock on an object you also reassign:
 *
 * ```
 * // wrong - the field is replaced while the OLD object is locked, so a second thread
 * // locks the NEW one and walks straight in
 * synchronized(table) { table = SomethingElse() }
 * ```
 *
 * Give the state a dedicated [AapsLock] and lock that instead; it cannot be swapped out from under
 * the callers.
 */
expect class AapsLock() {

    /** Runs [action] holding the lock, and returns whatever it returns. */
    fun <T> withLock(action: () -> T): T
}
