package app.aaps.plugins.dexcomoneplus

/**
 * Blocks the calling thread for [millis].
 *
 * The driver's session loops are written as blocking cycles on their own thread - write a request,
 * wait for the answer, settle, go again - so they need a blocking wait rather than a suspending
 * one. `Thread.sleep` is JVM only, which is the only reason this exists.
 *
 * @return true when the whole wait was served, false when it was cut short. On Android that means
 *   the thread was interrupted, and the interrupt flag is put back so the caller can still see it.
 *   Callers use the false to stop what they were doing, so this must never pretend it slept.
 */
internal expect fun onePlusSleep(millis: Long): Boolean
