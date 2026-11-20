package app.aaps.plugins.dexcomoneplus.reconnect

import app.aaps.plugins.dexcomoneplus.oem.OemDeviceProfile

/**
 * Reconnect / backoff policy. Uses [OemDeviceProfile] knobs only — no GATT.
 */
interface OnePlusReconnectPolicy {
    fun shouldRetry(attempt: Int, profile: OemDeviceProfile): Boolean
    fun nextDelayMs(attempt: Int, profile: OemDeviceProfile): Long
}

class OemAwareReconnectPolicy : OnePlusReconnectPolicy {

    override fun shouldRetry(attempt: Int, profile: OemDeviceProfile): Boolean {
        if (attempt < 0) return false
        return attempt < profile.connectRetryCount
    }

    override fun nextDelayMs(attempt: Int, profile: OemDeviceProfile): Long {
        val base = profile.connectRetryDelayMs.coerceAtLeast(0L)
        if (!profile.aggressiveReconnect) return base
        // First reconnect is quick — the sensor advertises in short windows, so a fast retry
        // catches the next one instead of parking for the full base delay (field: Samsung sat
        // 20 s idle after an auth=2 wipe before the full J-PAKE could run). Then grow linearly,
        // but cap so late attempts don't stretch to minute-scale gaps.
        val delay = if (attempt <= 1) FIRST_RETRY_MS else base * (attempt - 1)
        return delay.coerceAtMost(MAX_RECONNECT_DELAY_MS)
    }

    companion object {
        /** Delay before the first reconnect attempt (catch the next ADV window fast). */
        const val FIRST_RETRY_MS = 3_000L

        /** Upper bound on any reconnect backoff. */
        const val MAX_RECONNECT_DELAY_MS = 30_000L
    }
}
