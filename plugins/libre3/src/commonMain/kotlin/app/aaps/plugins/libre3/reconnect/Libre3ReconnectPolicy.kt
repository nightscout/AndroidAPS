package app.aaps.plugins.libre3.reconnect

import app.aaps.core.interfaces.source.SensorSlot

/** What the driver should do after a session attempt failed. */
enum class Libre3RecoveryAction {

    /** Wait and try the same short reconnect again. */
    RETRY_CACHED_RECONNECT,

    /**
     * Stop trying and ask the user to hold the phone on the sensor again.
     *
     * This is the only way out when the short reconnect keeps failing. Trying a first pairing
     * instead would be refused by a running sensor anyway.
     */
    ASK_FOR_NFC_SCAN,
}

/**
 * How often and how fast the driver tries again, and when it gives up and asks for a new scan.
 *
 * Pure, so it is unit tested without a radio.
 */
object Libre3ReconnectPolicy {

    /** Wait before the first retry. Short, so the next short radio window of the sensor is caught. */
    const val FIRST_RETRY_MS = 3_000L

    /** No wait of the quick ladder is ever longer than this. */
    const val MAX_DELAY_MS = 30_000L

    /**
     * The pace once the quick ladder is spent.
     *
     * A sensor that cannot be reached is usually a sensor out of range, or a radio busy with other
     * devices. Neither is helped by trying every three seconds for an hour, and neither is helped
     * by giving up: the sensor is on the arm for two weeks and its key is still good. So the driver
     * keeps knocking, slowly, and costs almost nothing while it waits.
     */
    const val SLOW_RETRY_MS = 60_000L

    /** How many quick attempts are made before the slow pace takes over. */
    const val MAX_ATTEMPTS = 6

    /** The pre-soak slot never retries faster than this. */
    const val STAGING_MIN_RETRY_MS = 20_000L

    /** Added to the pre-soak slot's wait, so the two slots do not knock at the same instant. */
    const val STAGING_RETRY_OFFSET_MS = 7_000L

    /**
     * @param attempt how many attempts already failed, starting at 1 for the first failure.
     * @param slot which slot is retrying. The pre-soak slot is slower on purpose: it has hours to
     *   succeed, and it must never push the sensor that feeds the loop out of the scan budget, see
     *   [Libre3ScanBudget]. The production slot gets exactly the pace it always had.
     */
    fun nextDelayMs(attempt: Int, slot: SensorSlot = SensorSlot.PRODUCTION): Long {
        val base = when {
            attempt <= 1            -> FIRST_RETRY_MS
            attempt >= MAX_ATTEMPTS -> SLOW_RETRY_MS
            else                    -> (FIRST_RETRY_MS * attempt).coerceAtMost(MAX_DELAY_MS)
        }
        return when (slot) {
            SensorSlot.PRODUCTION -> base
            SensorSlot.STAGING    -> (base + STAGING_RETRY_OFFSET_MS).coerceAtLeast(STAGING_MIN_RETRY_MS)
        }
    }

    /**
     * What to do after a failure, and the one thing that decides it: did the sensor talk to us?
     *
     * - **It did not.** The link never came up, or it died on its own. Nothing about that says the
     *   stored key is wrong, so a new NFC scan would fix nothing that another try does not fix.
     *   The driver keeps trying, for as long as the sensor stays stored.
     * - **It did, and it kept refusing us.** There the key really is the suspect, and a scan is
     *   the only way out, because a running sensor refuses a fresh first pairing.
     *
     * A field log showed why this matters: a link lost to a busy radio left the user with no
     * glucose for six minutes, and only a hand held over the sensor brought it back.
     *
     * @param handshakeReached true when the sensor answered the first pairing message.
     */
    fun actionAfterFailure(attempt: Int, handshakeReached: Boolean): Libre3RecoveryAction {
        if (!handshakeReached) return Libre3RecoveryAction.RETRY_CACHED_RECONNECT
        if (attempt >= MAX_ATTEMPTS) return Libre3RecoveryAction.ASK_FOR_NFC_SCAN
        return Libre3RecoveryAction.RETRY_CACHED_RECONNECT
    }
}
