package app.aaps.plugins.libre3.session

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.plugins.libre3.Libre3Log
import app.aaps.plugins.libre3.Libre3LogMarkers
import app.aaps.plugins.libre3.Libre3LogMask

/**
 * Gives the physical sensor an owner.
 *
 * The pre-soak separates everything that can be separated — preferences file, executor, driver
 * instance — and the one thing it cannot separate is the sensor itself. Two drivers on one Libre 3
 * means two `BluetoothGatt` clients over a single link, both subscribed to the same characteristics,
 * so each receives the other's notifications and the sensor sees one interleaved stream of two
 * pairings. Neither side finishes.
 *
 * Why a process wide claim and not only a check on the Start screen: the Start screen is not the
 * only way in. The plugin resumes **both** slots on every start with no UI involved, so an install
 * whose two files already hold one sensor would reproduce the collision on every launch. A claim
 * taken where the link is really opened covers those paths, and the ones not written yet.
 *
 * ⚠️ The owner token must **not** be the driver's slot name. `Libre3CgmDriverReal` keeps its slot
 * name for the whole life of the instance, on purpose, so a bug report stays readable across a
 * promotion — a promoted instance is production but still calls itself "presoak". Keying the map on
 * that name would let the next pre-soak take over the promoted instance's claim, and the invariant
 * would break after the very first promotion. Each driver instance passes a token of its own
 * instead.
 *
 * An owner may re-claim what it already holds: reconnects and repairs inside one slot are normal,
 * and claiming a different sensor releases the owner's previous claim.
 */
object Libre3MacArbiter {

    /** Guards [owners]. An [AapsLock] rather than `synchronized`, which is JVM only. */
    private val lock = AapsLock()

    /** Normalised MAC → the token of the driver instance that owns it. At most one owner per sensor. */
    private val owners = mutableMapOf<String, String>()

    private fun normalize(mac: String): String = mac.trim().uppercase()

    /**
     * Take ownership of [mac] for [owner].
     *
     * @param owner a token that belongs to one driver instance and never changes, see the note on
     *   promotion above.
     * @return true when that driver may open a link. false means another driver instance holds this
     *   sensor and the caller must not open a session on it.
     */
    fun claim(mac: String, owner: String): Boolean {
        val key = normalize(mac).takeIf { it.isNotEmpty() } ?: return false
        lock.withLock {
            val current = owners[key]
            if (current != null && current != owner) {
                Libre3Log.w(
                    "${Libre3LogMarkers.SESSION}: [$owner] sensor claim refused — ${Libre3LogMask.mac(key)} " +
                        "is held by [$current]; two links on one sensor break both pairings",
                )
                return false
            }
            // One driver instance holds one sensor at a time: moving to another releases the old one.
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
