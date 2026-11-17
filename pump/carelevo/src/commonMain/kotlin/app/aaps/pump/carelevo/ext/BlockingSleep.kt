package app.aaps.pump.carelevo.ext

/**
 * Blocks the calling thread for [ms] milliseconds, also when the thread is interrupted, as Android's
 * `SystemClock.sleep` does.
 *
 * The bolus and profile coordinators run on the command queue's thread and wait there on purpose: the queue must not
 * start the next command before the patch is done.
 */
internal expect fun sleepBlocking(ms: Long)
