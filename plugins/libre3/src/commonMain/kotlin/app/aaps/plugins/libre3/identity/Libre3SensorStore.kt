package app.aaps.plugins.libre3.identity

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.plugins.libre3.Libre3Log
import app.aaps.plugins.libre3.Libre3LogMarkers
import app.aaps.plugins.libre3.nfc.Libre3NfcCommands
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The small part of the store that the NFC step needs.
 *
 * It exists so the NFC flow can be unit tested with a simple fake, without an Android context and
 * without touching a real settings file.
 */
interface Libre3IdentityStore {

    /** Identifier of this phone, created once per install. */
    fun receiverId(): Int

    /**
     * Writes the sensor and waits for the write to reach the disk.
     *
     * @return true only when the write really finished.
     */
    fun saveIdentityAndWait(identity: Libre3SensorIdentity): Boolean
}

/**
 * The part of the store that one Bluetooth session needs.
 *
 * It exists for the same reason as [Libre3IdentityStore]: so the whole session, including the
 * rule that a first pairing must store its key before it may report success, can be unit tested
 * with a simple fake and no Android context.
 */
interface Libre3SessionStore {

    /**
     * The sensor this phone last took over, or null when there is none.
     *
     * An implementation must only ever return a sensor whose PIN write really finished. The
     * session leans on that: it treats "a sensor is stored" as "its PIN is safely on the disk",
     * and no connect may start before that write has landed. An
     * implementation that returned a half written sensor would break that rule silently.
     */
    fun loadIdentity(): Libre3SensorIdentity?

    /** The keys of that sensor. Any of them may be missing. */
    fun loadSessionKeys(): Libre3SessionKeys

    /**
     * Writes the pairing key of a first pairing and waits for it to reach the disk.
     *
     * @return true only when the write really finished. A false here must fail the pairing: a
     *   sensor that is paired but whose key was not written can never be reconnected to.
     */
    fun savePhase5RawKeyAndWait(phase5RawKey: ByteArray): Boolean

    /**
     * Drops the stored pairing key and waits for that to reach the disk.
     *
     * Only for the one case where the key is known to be worthless: the sensor refused our Phase 5
     * answer, so it never authorised this phone and never learned this key. Keeping it would make
     * every later attempt take the short reconnect path with a key the sensor does not have, which
     * can only fail, and the driver would then ask for an NFC scan that cannot help either.
     *
     * @return true only when the change really finished.
     */
    fun clearPhase5RawKeyAndWait(): Boolean

    /** Stores the keys of the session that just started. */
    fun saveSessionKeys(kEnc: ByteArray, ivEnc: ByteArray)
}

/**
 * Keeps the sensor identity, the session keys and the ingest high-water mark on disk.
 *
 * A private key-value file is used, not the exportable AAPS settings, so the PIN and the keys
 * never leave the phone in a settings export.
 *
 * The writes that the Bluetooth work depends on use [Libre3SensorPrefs.editAndWait], not
 * [Libre3SensorPrefs.edit]. The rule is that the PIN must really be on disk before any connect
 * starts, so a crash in the middle can never leave a sensor that was taken over but whose PIN is
 * lost.
 *
 * Which slot this store belongs to is decided by which file it is given, see [prefsFileName]. A
 * pre-soak write must never be able to reach the production keys, because [saveIdentityAndWait]
 * drops the pairing key when the serial changes and a running sensor refuses a fresh first pairing.
 */
@OptIn(ExperimentalEncodingApi::class, ExperimentalUuidApi::class)
class Libre3SensorStore(
    /** This slot's own file, named by [prefsFileName]. */
    private val prefs: Libre3SensorPrefs,
    /**
     * The phone's own identity, always the production file.
     *
     * The receiver id is the phone's identity, not a sensor's. A second file with a second id would
     * give one phone two identities, and a sensor binds itself to the receiver that activated it.
     * For the production slot this is the same object as [prefs].
     */
    private val identityPrefs: Libre3SensorPrefs = prefs,
    /** Only for log lines, so a bug report says which file was written. */
    private val prefsName: String = PREFS_NAME,
) : Libre3IdentityStore, Libre3SessionStore {

    /**
     * Guards the writes that used to be `@Synchronized`, which is JVM only. Reentrant the same way
     * the object monitor was, so a guarded method may still call another one.
     */
    private val lock = AapsLock()

    /**
     * Identifier this phone shows to sensors, kept for the whole install.
     *
     * It is built once from a fresh random id and then reused, because the sensor binds itself to
     * the receiver that activated it. The test id from the LibreCRKit tests is never used here.
     *
     * It is not kept in the Android Keystore. The Keystore stores keys, not free
     * text, so this value lives in the app private settings file instead. It is not a secret. It
     * only has to be stable and different from other installs.
     */
    override fun receiverId(): Int = lock.withLock {
        val storedUuid = identityPrefs.getString(KEY_APP_UUID)
        if (storedUuid != null) return identityPrefs.getInt(KEY_RECEIVER_ID, Libre3NfcCommands.receiverIdFrom(storedUuid))
        val uuid = Uuid.random().toString()
        val receiverId = Libre3NfcCommands.receiverIdFrom(uuid)
        val written = identityPrefs.editAndWait {
            putString(KEY_APP_UUID, uuid)
            putInt(KEY_RECEIVER_ID, receiverId)
        }
        // Sending an id that was not stored would bind the sensor to a number this phone can never
        // build again, and the sensor would stop answering us.
        check(written) { "the receiver id of this phone could not be stored" }
        Libre3Log.i("${Libre3LogMarkers.NFC}: new receiver id created for this install")
        return receiverId
    }

    /**
     * Writes everything the NFC scan produced and waits for the write to finish.
     *
     * @return true when the write really reached the disk. The caller must not start Bluetooth when
     *   this is false.
     */
    override fun saveIdentityAndWait(identity: Libre3SensorIdentity): Boolean = lock.withLock {
        val serialChanged = identity.serialNumber != prefs.getString(KEY_SERIAL)
        val written = prefs.editAndWait {
            putString(KEY_SERIAL, identity.serialNumber)
            putString(KEY_MAC, identity.bleAddress)
            putString(KEY_PIN, encode(identity.blePin))
            putInt(KEY_RECEIVER_ID, identity.receiverId)
            putInt(KEY_GENERATION, identity.generation)
            putInt(KEY_WARMUP_MINUTES, identity.warmupMinutes)
            putInt(KEY_WEAR_MINUTES, identity.wearDurationMinutes)
            putLong(KEY_ACTIVATED_AT, identity.activatedAtMs)
            if (serialChanged) {
                // A different sensor starts its own life counter and needs its own keys. Keeping the
                // old ones would either block every new reading as "already seen" or send a stale
                // key. The sensor change mark goes too, so a sensor that is put back on later is
                // written again.
                remove(KEY_LAST_LIFE_COUNT)
                remove(KEY_PHASE5_RAW_KEY)
                remove(KEY_K_ENC)
                remove(KEY_IV_ENC)
                remove(KEY_SENSOR_CHANGE_SERIAL)
            }
        }
        Libre3Log.i(
            "${Libre3LogMarkers.NFC}: sensor stored serialChanged=$serialChanged written=$written",
        )
        return written
    }

    /** The stored sensor, or null when no sensor was ever scanned. */
    override fun loadIdentity(): Libre3SensorIdentity? {
        val serial = prefs.getString(KEY_SERIAL)?.takeIf { it.isNotBlank() } ?: return null
        val mac = prefs.getString(KEY_MAC)?.takeIf { it.isNotBlank() } ?: return null
        val pin = decode(prefs.getString(KEY_PIN))?.takeIf { it.size == PIN_SIZE } ?: return null
        return Libre3SensorIdentity(
            serialNumber = serial,
            bleAddress = mac,
            blePin = pin,
            receiverId = prefs.getInt(KEY_RECEIVER_ID, 0),
            generation = prefs.getInt(KEY_GENERATION, 0),
            warmupMinutes = prefs.getInt(KEY_WARMUP_MINUTES, DEFAULT_WARMUP_MINUTES),
            wearDurationMinutes = prefs.getInt(KEY_WEAR_MINUTES, 0),
            activatedAtMs = prefs.getLong(KEY_ACTIVATED_AT, 0L),
        )
    }

    /** True when a sensor with a PIN is stored, which is the only case where Bluetooth may start. */
    fun isReadyForBle(): Boolean = loadIdentity() != null

    /** Session keys of the stored sensor. All three parts may be null. */
    override fun loadSessionKeys(): Libre3SessionKeys =
        Libre3SessionKeys(
            phase5RawKey = decode(prefs.getString(KEY_PHASE5_RAW_KEY))?.takeIf { it.size == PHASE5_KEY_SIZE },
            kEnc = decode(prefs.getString(KEY_K_ENC))?.takeIf { it.size == K_ENC_SIZE },
            ivEnc = decode(prefs.getString(KEY_IV_ENC))?.takeIf { it.size == IV_ENC_SIZE },
        )

    /**
     * Stores the key of the first pairing. It is written once per sensor and then reused by every
     * reconnect, so it must survive the app being killed.
     */
    override fun savePhase5RawKeyAndWait(phase5RawKey: ByteArray): Boolean = lock.withLock {
        prefs.editAndWait { putString(KEY_PHASE5_RAW_KEY, encode(phase5RawKey)) }
    }

    /** Drops the pairing key of a pairing the sensor refused. */
    override fun clearPhase5RawKeyAndWait(): Boolean = lock.withLock {
        prefs.editAndWait { remove(KEY_PHASE5_RAW_KEY) }
    }

    /** Stores the keys of the current session. A new handshake replaces them. */
    override fun saveSessionKeys(kEnc: ByteArray, ivEnc: ByteArray) = lock.withLock {
        prefs.edit {
            putString(KEY_K_ENC, encode(kEnc))
            putString(KEY_IV_ENC, encode(ivEnc))
        }
    }

    /**
     * Gives the sensor more time when it is still sending past the end this phone thinks it has.
     *
     * A sensor that keeps sending good readings is alive, whatever a stored number says. Without
     * this, one bad NFC reading of the wear time would make the driver treat a healthy sensor as
     * finished and drop every reading from then on, in silence. The upstream project added the
     * same rule for the same reason.
     *
     * @return the wear time to use from now on.
     */
    fun extendWearIfStillAlive(lifeCountMinutes: Int): Int = lock.withLock {
        val stored = prefs.getInt(KEY_WEAR_MINUTES, 0)
        if (stored <= 0 || lifeCountMinutes < stored) return stored
        val extended = lifeCountMinutes + WEAR_EXTENSION_MINUTES
        prefs.editAndWait { putInt(KEY_WEAR_MINUTES, extended) }
        Libre3Log.i(
            "${Libre3LogMarkers.SESSION}: the sensor is still sending past its stored end, " +
                "wear time moved from $stored to $extended minutes",
        )
        return extended
    }

    /** Highest sensor life counter that was sent to AAPS, or -1 when there is none. */
    fun loadLastLifeCount(): Int = prefs.getInt(KEY_LAST_LIFE_COUNT, -1)

    /**
     * Remembers the last life counter, so a restart cannot send the same reading twice.
     *
     * This one is written with `commit`, not `apply`: a crash between the insert and a lazy write
     * would let the same reading in again, and the loop treats a repeated reading as an error.
     */
    fun saveLastLifeCount(lifeCount: Int) {
        prefs.editAndWait { putInt(KEY_LAST_LIFE_COUNT, lifeCount) }
    }

    /**
     * Serial of the sensor whose start was already written as a sensor change, or null when none
     * was written yet. It is what keeps that event unique per sensor across restarts.
     */
    fun loadSensorChangeLoggedSerial(): String? =
        prefs.getString(KEY_SENSOR_CHANGE_SERIAL)?.takeIf { it.isNotBlank() }

    /**
     * Remembers that the start of [serialNumber] has been written as a sensor change.
     *
     * `apply` is enough here, unlike the writes the Bluetooth work depends on: the therapy event
     * itself is already in the database and a second write of the same moment is refused there, so
     * a lost mark can only cost one repeated and harmless insert.
     */
    fun saveSensorChangeLoggedSerial(serialNumber: String) {
        if (serialNumber.isBlank()) return
        lock.withLock { prefs.edit { putString(KEY_SENSOR_CHANGE_SERIAL, serialNumber) } }
    }

    /** Forgets the sensor and its keys. Used when the user starts a different sensor. */
    fun clear() {
        // The receiver id and its unique text stay, because this phone keeps its identity.
        lock.withLock {
            prefs.editAndWait {
                remove(KEY_SERIAL)
                remove(KEY_MAC)
                remove(KEY_PIN)
                remove(KEY_GENERATION)
                remove(KEY_WARMUP_MINUTES)
                remove(KEY_WEAR_MINUTES)
                remove(KEY_ACTIVATED_AT)
                remove(KEY_LAST_LIFE_COUNT)
                remove(KEY_PHASE5_RAW_KEY)
                remove(KEY_K_ENC)
                remove(KEY_IV_ENC)
                remove(KEY_SENSOR_CHANGE_SERIAL)
            }
        }
    }

    /**
     * Wipes this slot's file completely.
     *
     * Only for a pre-soak file. It must never be called on the production file: that one also holds
     * the receiver id of this phone, and this call would take it away. A pre-soak file does not hold
     * the receiver id at all, so nothing shared can be lost here.
     *
     * @return true only when the change really reached the disk.
     */
    fun clearAll(): Boolean = lock.withLock { prefs.editAndWait { clear() } }

    /**
     * Keeps this slot's collect-only progress, so a restart does not send a warming pre-soak back to
     * "no sensor".
     */
    fun saveSlotProgress(present: Boolean, validReadingCount: Int) {
        lock.withLock {
            prefs.editAndWait {
                putBoolean(KEY_SLOT_PRESENT, present)
                putInt(KEY_SLOT_VALID_READINGS, validReadingCount)
            }
        }
    }

    /** True when this slot holds a sensor the plugin should pick up again after a restart. */
    fun loadSlotPresent(): Boolean = prefs.getBoolean(KEY_SLOT_PRESENT, false)

    /** How many good readings this slot has collected, 0 when none. */
    fun loadSlotValidReadingCount(): Int = prefs.getInt(KEY_SLOT_VALID_READINGS, 0)

    /**
     * Latch: this slot's sensor has left warm-up at least once.
     *
     * It is a latch and not a live reading of the driver phase, because a healthy sensor reconnects
     * for its whole life and a reconnect must not look like a new warm-up.
     */
    fun saveSlotWarmupDone(done: Boolean) {
        lock.withLock { prefs.editAndWait { putBoolean(KEY_SLOT_WARMUP_DONE, done) } }
    }

    /** True once this slot's sensor has left warm-up. */
    fun loadSlotWarmupDone(): Boolean = prefs.getBoolean(KEY_SLOT_WARMUP_DONE, false)

    /** Time this slot's sensor was really activated, in epoch milliseconds. */
    fun saveSlotActivatedAt(epochMs: Long) {
        lock.withLock { prefs.editAndWait { putLong(KEY_SLOT_ACTIVATED_AT, epochMs) } }
    }

    /** Time this slot's sensor was really activated, or 0 when it is not known. */
    fun loadSlotActivatedAt(): Long = prefs.getLong(KEY_SLOT_ACTIVATED_AT, 0L)

    /**
     * Takes another slot's sensor over into this file, so the driver of this slot resumes that
     * sensor after a restart. Used by the promotion of a pre-soak sensor.
     *
     * One `commit`, so it either all lands or none of it does. It does **not** go through
     * [saveIdentityAndWait], because that method drops the pairing key when the serial changes, and
     * that is exactly the key this call has just been asked to install.
     *
     * Two keys are dropped on purpose. The last life counter belongs to the old sensor and would
     * refuse every reading of the new one for its whole life. The sensor change mark has to go so
     * the start of the new sensor is written as an event.
     *
     * The receiver id is **not** written. It is the identity of this phone, it always lives in the
     * production file, and it is read from [identityPrefs], never from this slot's file. Writing it
     * here would put a second copy into whichever file this call happens to target, and on a
     * pre-soak file that would be a second identity for one phone.
     *
     * @param keys session keys of the taken over sensor. A part that is null is not written, so a
     *   sensor taken over in mid life does not get an empty value where a key is expected.
     * @return true only when the write really reached the disk.
     */
    fun adopt(identity: Libre3SensorIdentity, keys: Libre3SessionKeys): Boolean = lock.withLock {
        val written = prefs.editAndWait {
            putString(KEY_SERIAL, identity.serialNumber)
            putString(KEY_MAC, identity.bleAddress)
            putString(KEY_PIN, encode(identity.blePin))
            putInt(KEY_GENERATION, identity.generation)
            putInt(KEY_WARMUP_MINUTES, identity.warmupMinutes)
            putInt(KEY_WEAR_MINUTES, identity.wearDurationMinutes)
            putLong(KEY_ACTIVATED_AT, identity.activatedAtMs)
            remove(KEY_LAST_LIFE_COUNT)
            remove(KEY_SENSOR_CHANGE_SERIAL)
            keys.phase5RawKey?.let { putString(KEY_PHASE5_RAW_KEY, encode(it)) }
            keys.kEnc?.let { putString(KEY_K_ENC, encode(it)) }
            keys.ivEnc?.let { putString(KEY_IV_ENC, encode(it)) }
        }
        Libre3Log.i(
            "${Libre3LogMarkers.SESSION}: sensor taken over into $prefsName, " +
                "pairingKey=${keys.phase5RawKey != null} written=$written",
        )
        return written
    }

    // The shared Base64: the same alphabet, padded, with no line breaks, so the text already on
    // the disk reads back byte for byte.
    private fun encode(value: ByteArray): String = Base64.encode(value)

    private fun decode(value: String?): ByteArray? =
        value?.let {
            try {
                Base64.decode(it)
            } catch (_: Throwable) {
                null
            }
        }

    companion object {

        private const val PREFS_NAME = "libre3_sensor_store"

        /**
         * The file name of one slot.
         *
         * @param namespace null or blank is the production slot and maps to the file every install
         *   already has, so nothing is copied and nothing can be half copied. A name gives a
         *   second, separate file, used by the pre-soak slot.
         */
        fun prefsFileName(namespace: String? = null): String =
            if (namespace.isNullOrBlank()) PREFS_NAME else "${PREFS_NAME}_$namespace"

        private const val KEY_APP_UUID = "app_uuid"
        private const val KEY_RECEIVER_ID = "receiver_id"
        private const val KEY_SERIAL = "serial"
        private const val KEY_MAC = "ble_mac"
        private const val KEY_PIN = "ble_pin"
        private const val KEY_GENERATION = "generation"
        private const val KEY_WARMUP_MINUTES = "warmup_minutes"
        private const val KEY_WEAR_MINUTES = "wear_minutes"
        private const val KEY_ACTIVATED_AT = "activated_at"
        private const val KEY_LAST_LIFE_COUNT = "last_life_count"
        private const val KEY_SENSOR_CHANGE_SERIAL = "sensor_change_logged_serial"
        private const val KEY_PHASE5_RAW_KEY = "phase5_raw_key"
        private const val KEY_K_ENC = "k_enc"
        private const val KEY_IV_ENC = "iv_enc"

        // Slot progress. Only a pre-soak slot writes these today, but they are plain per-file keys
        // and the production file simply never sets them.
        private const val KEY_SLOT_PRESENT = "slot_present"
        private const val KEY_SLOT_VALID_READINGS = "slot_valid_readings"
        private const val KEY_SLOT_WARMUP_DONE = "slot_warmup_done"
        private const val KEY_SLOT_ACTIVATED_AT = "slot_activated_at"

        private const val PIN_SIZE = 4
        private const val PHASE5_KEY_SIZE = 16
        private const val K_ENC_SIZE = 16
        private const val IV_ENC_SIZE = 8

        /** Used only when the sensor did not tell us its warm-up length. */
        const val DEFAULT_WARMUP_MINUTES = 60

        /** How much more time a sensor is given when it is still sending past its stored end. */
        const val WEAR_EXTENSION_MINUTES = 24 * 60
    }
}
