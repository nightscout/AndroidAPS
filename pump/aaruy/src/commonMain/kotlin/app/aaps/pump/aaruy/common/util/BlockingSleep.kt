package app.aaps.pump.aaruy.common.util

/**
 * Blocks the calling thread for [ms] milliseconds, also when the thread is interrupted, as Android's
 * `SystemClock.sleep` does.
 *
 * The driver's commands run on the command queue's thread and wait there on purpose: the queue must not start the next
 * command before the pump has answered this one.
 */
internal expect fun sleepBlocking(ms: Long)
