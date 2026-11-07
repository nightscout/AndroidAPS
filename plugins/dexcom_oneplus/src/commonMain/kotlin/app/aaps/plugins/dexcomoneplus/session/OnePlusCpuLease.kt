package app.aaps.plugins.dexcomoneplus.session

/**
 * Keeps the phone's processor running while the driver does something that must not be paused.
 *
 * The session connects, authenticates and reads its first value on one thread, with short waits in
 * between. Those waits are measured on a clock that stops while the phone sleeps, so with the
 * screen off a 500 ms handoff was once measured taking 27.7 s (field log 26/08/2026) and the
 * connection missed the advertisement it had just heard.
 *
 * The lease says what is wanted - "do not let the phone go to sleep for a while" - and leaves each
 * platform to say how. On Android that is a partial wake lock.
 */
interface OnePlusCpuLease {

    /**
     * Takes the lease, for at most [maxMillis] in case the holder never gives it back.
     *
     * @return the lease being held, or null when the platform could not take one. Null is not an
     *   error the caller has to handle: the work goes ahead, it is only less protected.
     */
    fun hold(maxMillis: Long): Hold?

    /** A lease that is being held. */
    interface Hold {

        /** Gives the lease back. Safe to call when it has already ended on its own. */
        fun release()
    }
}
