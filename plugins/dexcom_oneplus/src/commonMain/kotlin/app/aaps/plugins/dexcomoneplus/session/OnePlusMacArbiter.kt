package app.aaps.plugins.dexcomoneplus.session

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.plugins.dexcomoneplus.OnePlusLog
import app.aaps.plugins.dexcomoneplus.OnePlusLogMarkers

/**
 * Gives the physical sensor an owner.
 *
 * The dual-slot design separates everything that can be separated — store namespace, executor,
 * driver instance — and the one thing it cannot separate is the transmitter itself. Two slots on one
 * MAC means two `BluetoothGatt` clients over a single ACL link, both subscribed to the same Auth and
 * ExtraData characteristics, so each receives the other's notifications and the sensor sees one
 * interleaved stream of two KEKS handshakes. The field log of 2026-08-25 shows both sides dying
 * inside libkeks 1.6 s after the second connect, on a handshake that had just reported
 * `authenticated=1`. It is not a race that sometimes bites; it is certain.
 *
 * Why a process-wide claim and not only a check at the two UI entry points: the entry points are not
 * the only way in. `resumeStoredSession` runs for both slots on every plugin start, and for an
 * install whose two stores already hold the same MAC — the state a phone is left in by the very bug
 * above — that reproduced the collision on every launch with no UI involved. A claim taken where the
 * connection is actually made covers those paths, and the ones not written yet.
 *
 * An owner may re-claim what it already holds: reconnects and repairs inside one slot are normal, and
 * claiming a different MAC releases the owner's previous claim.
 *
 * ⚠️ The owner token must **not** be the driver's slot name ("prod" / "staging"). `OnePlusCgmDriverReal`
 * keeps its slot name for the whole life of the instance, on purpose, so a bug report stays readable
 * across a promotion — a promoted instance is production but still calls itself "staging" in the log.
 * Keying the map on that name would let the next pre-soak's `claim` remove the promoted instance's own
 * claim (see the "moving to another releases the old one" rule below), silently dropping the arbiter's
 * protection on the sensor that is still feeding the loop. Each driver instance passes a token of its
 * own instead — see `OnePlusCgmDriverReal.arbiterOwner`.
 */
object OnePlusMacArbiter {

    private val lock = AapsLock()

    /** Normalised MAC → the token of the driver instance that owns it. At most one owner per transmitter. */
    private val owners = mutableMapOf<String, String>()

    private fun normalize(mac: String): String = mac.trim().uppercase()

    /**
     * Take ownership of [mac] for [owner].
     *
     * @param owner a token that belongs to one driver instance and never changes — see the note on
     *   promotion above.
     * @return true when that driver may connect. false means another driver instance holds this
     *   transmitter and the caller must not open a session on it.
     */
    fun claim(mac: String, owner: String): Boolean {
        val key = normalize(mac).takeIf { it.isNotEmpty() } ?: return false
        lock.withLock {
            val current = owners[key]
            if (current != null && current != owner) {
                OnePlusLog.w(
                    "${OnePlusLogMarkers.SESSION}: [$owner] MAC claim refused — ***${key.takeLast(5)} " +
                        "is held by [$current]; two sessions on one sensor corrupt the KEKS handshake",
                )
                return false
            }
            // One driver instance holds one transmitter at a time: moving to another releases the old one.
            owners.entries.removeAll { it.value == owner && it.key != key }
            owners[key] = owner
            return true
        }
    }

    /** Give up whatever [owner] holds. Safe when it holds nothing. */
    fun release(owner: String) {
        lock.withLock {
            owners.entries.removeAll { it.value == owner }
        }
    }

    /** Which owner holds [mac], or null when nobody does. */
    fun ownerOf(mac: String): String? {
        val key = normalize(mac).takeIf { it.isNotEmpty() } ?: return null
        return lock.withLock { owners[key] }
    }

    /** Test hook: forget every claim. */
    fun reset() {
        lock.withLock { owners.clear() }
    }
}
