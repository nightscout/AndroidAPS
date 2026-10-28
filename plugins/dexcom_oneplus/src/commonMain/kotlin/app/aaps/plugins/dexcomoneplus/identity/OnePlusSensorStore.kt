package app.aaps.plugins.dexcomoneplus.identity

import app.aaps.plugins.dexcomoneplus.OnePlusLog
import app.aaps.plugins.dexcomoneplus.OnePlusLogMarkers
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Persist ONE+ identity + last good MAC + KEKS shared key for Juggluco-style reconnect.
 *
 * Uses the slot's own private key-value file ([OnePlusSensorPrefs]), never the exportable AAPS
 * preferences — PIN / key stay local.
 */
@OptIn(ExperimentalEncodingApi::class)
class OnePlusSensorStore(private val prefs: OnePlusSensorPrefs) {

    fun load(): OnePlusStoredSession? {
        val pin = prefs.getString(KEY_PIN)?.takeIf { it.length == 4 } ?: return null
        val serial = prefs.getString(KEY_SERIAL)?.takeIf { it.isNotBlank() }
        val gtin = prefs.getString(KEY_GTIN)?.takeIf { it.isNotBlank() }
        val raw = prefs.getString(KEY_RAW_GS1)?.takeIf { it.isNotBlank() }
        val mac = prefs.getString(KEY_MAC)?.takeIf { it.isNotBlank() }
        val deviceName = prefs.getString(KEY_DEVICE_NAME)?.takeIf { it.isNotBlank() }
        val keyB64 = prefs.getString(KEY_SHARED)
        val shared = keyB64?.let {
            try {
                Base64.decode(it)
            } catch (_: Throwable) {
                null
            }
        }?.takeIf { it.size == 16 }
        return OnePlusStoredSession(
            identity = OnePlusSensorIdentity(pin = pin, serial = serial, gtin = gtin, rawGs1 = raw),
            lastMac = mac,
            lastDeviceName = deviceName,
            sharedKey = shared,
        )
    }

    fun saveIdentity(identity: OnePlusSensorIdentity) {
        // A new sensor restarts its EGV sequence counter from a low value, so the persisted ingest
        // high-water mark (last sequence/timestamp) MUST be reset — otherwise the new sensor's early
        // readings would be wrongly rejected as "already seen". Reset only on an actual serial change.
        val serialChanged = identity.serial != null && identity.serial != prefs.getString(KEY_SERIAL)
        prefs.edit {
            putString(KEY_PIN, identity.pin)
            putString(KEY_SERIAL, identity.serial)
            putString(KEY_GTIN, identity.gtin)
            putString(KEY_RAW_GS1, identity.rawGs1)
        }
        // The session start is NOT cleared here. It is owned solely by [startSessionForSensor], which
        // decides from the MAC whether this is the same sensor. Clearing it here made the write order
        // matter: the staging start recorded the session start first, then saveIdentity wiped it
        // milliseconds later, so the soak clock restarted from the first reading instead of from the
        // moment the sensor was applied.
        if (serialChanged) clearLastIngest()
        OnePlusLog.i(
            "${OnePlusLogMarkers.SESSION}: sensor identity saved serial=${maskSerial(identity.serial)} serialChanged=$serialChanged",
        )
    }

    /**
     * Persist the ingest high-water mark so a process restart / app update cannot re-insert already
     * stored readings (which the loop rejects as duplicates). [sequence] is the monotonic per-sensor
     * EGV counter; [timestampMs] backs the near-duplicate time window.
     */
    fun saveLastIngest(sequence: Long?, timestampMs: Long) {
        prefs.edit {
            if (sequence != null) putLong(KEY_LAST_SEQ, sequence)
            putLong(KEY_LAST_TS, timestampMs)
        }
    }

    /** Last ingested EGV sequence, or -1 when none recorded (accept everything). */
    fun loadLastIngestSequence(): Long = prefs.getLong(KEY_LAST_SEQ, -1L)

    /** Last ingested reading timestamp (ms), or 0 when none recorded. */
    fun loadLastIngestTimestamp(): Long = prefs.getLong(KEY_LAST_TS, 0L)

    private fun clearLastIngest() {
        prefs.edit {
            remove(KEY_LAST_SEQ)
            remove(KEY_LAST_TS)
        }
    }

    /**
     * Persist the slot's collect-only progress so it survives a process restart. Used by the STAGING
     * slot: without it, `stagingPresent` / `stagingValidEgvCount` are in-memory only, so any restart
     * reset a warming pre-soak sensor to ABSENT — it could then neither resume, leave warm-up, nor be
     * promoted, even though this store still held its identity, MAC and KEKS key.
     */
    fun saveSlotProgress(present: Boolean, validEgvCount: Int) {
        prefs.edit {
            putBoolean(KEY_SLOT_PRESENT, present)
            putInt(KEY_SLOT_EGV_COUNT, validEgvCount.coerceAtLeast(0))
        }
    }

    /** A sensor was started in this slot and not cancelled/promoted. */
    fun loadSlotPresent(): Boolean = prefs.getBoolean(KEY_SLOT_PRESENT, false)

    /** Valid EGVs collected in this slot so far (promotion gate). */
    fun loadSlotValidEgvCount(): Int = prefs.getInt(KEY_SLOT_EGV_COUNT, 0)

    /**
     * Latch: this slot's sensor has finished warm-up at least once.
     *
     * Warm-up completion is an event, not a state that can be read back from the live connection
     * phase — a healthy sensor keeps re-connecting every radio cycle for its whole life. Persisted so
     * a restart does not send a settled sensor back to "warming up".
     */
    fun saveSlotWarmupDone(done: Boolean) {
        prefs.edit { putBoolean(KEY_SLOT_WARMUP_DONE, done) }
    }

    /** True once this slot's sensor has left warm-up (see [saveSlotWarmupDone]). */
    fun loadSlotWarmupDone(): Boolean = prefs.getBoolean(KEY_SLOT_WARMUP_DONE, false)

    /** Record the sensor session start (epoch ms) once — used to derive early/end-of-life age. */
    fun saveSessionStartIfAbsent(epochMs: Long) {
        if (!prefs.contains(KEY_SESSION_START)) prefs.edit { putLong(KEY_SESSION_START, epochMs) }
    }

    /**
     * Anchor the session start on the explicit sensor start the user just ran, so the sensor age can
     * be known from that moment instead of only from the first reading (~30 min later, after warm-up).
     *
     * Re-connecting the **same** sensor keeps the stored start — re-pairing a running sensor must not
     * rejuvenate its age. Another sensor restarts the clock. The owning MAC is kept in its own key,
     * so this does not depend on the order in which [saveLastMac] / [saveIdentity] are called
     * around it; see [startsNewSession] for the decision itself.
     *
     * @param previousMac MAC stored for the running session **before** this start. Only used for
     *   sessions anchored by [saveSessionStartIfAbsent] before the owner key existed.
     * @return true when a new session start was written (the caller may then log a sensor change).
     */
    fun startSessionForSensor(deviceAddress: String, epochMs: Long, previousMac: String?): Boolean {
        if (deviceAddress.isBlank() || epochMs <= 0L) return false
        val address = deviceAddress.uppercase()
        val isNewSession = startsNewSession(
            storedStartMs = prefs.getLong(KEY_SESSION_START, 0L),
            storedOwnerMac = prefs.getString(KEY_SESSION_START_MAC),
            previousMac = previousMac,
            deviceAddress = address,
        )
        if (!isNewSession) {
            // Same sensor: keep its age, and adopt the owner key so a session anchored before that
            // key existed is recognised directly next time.
            prefs.edit { putString(KEY_SESSION_START_MAC, address) }
            return false
        }
        prefs.edit {
            putLong(KEY_SESSION_START, epochMs)
            putString(KEY_SESSION_START_MAC, address)
        }
        OnePlusLog.i(
            "${OnePlusLogMarkers.SESSION}: sensor session start recorded startMs=$epochMs",
        )
        return true
    }

    /** Sensor session start (epoch ms), or 0 when unknown. */
    fun loadSessionStart(): Long = prefs.getLong(KEY_SESSION_START, 0L)

    /**
     * Move the session start of the sensor already stored here — the user correcting the real
     * insertion time by hand.
     *
     * The other writers set this clock once and never touch it again on purpose ([saveSessionStartIfAbsent],
     * [startSessionForSensor]), so nothing else in the app could change a sensor age that was stamped
     * at pairing time instead of at insertion time. This is the one deliberate exception, and the
     * owner MAC is left as it is: the sensor has not changed, only the moment it went in.
     */
    fun overwriteSessionStart(epochMs: Long) {
        if (epochMs <= 0L) return
        prefs.edit { putLong(KEY_SESSION_START, epochMs) }
        OnePlusLog.i(
            "${OnePlusLogMarkers.SESSION}: sensor session start corrected by user startMs=$epochMs",
        )
    }

    fun saveLastMac(address: String) {
        if (address.isBlank()) return
        prefs.edit { putString(KEY_MAC, address.uppercase()) }
    }

    fun saveLastDeviceName(name: String?) {
        if (name.isNullOrBlank()) return
        prefs.edit { putString(KEY_DEVICE_NAME, name) }
    }

    fun saveSharedKey(key: ByteArray) {
        if (key.size != 16) return
        prefs.edit { putString(KEY_SHARED, Base64.encode(key)) }
        OnePlusLog.i(
            "${OnePlusLogMarkers.SESSION}: KEKS shared key persisted (16b)",
        )
    }

    fun clearSharedKey() {
        prefs.edit { remove(KEY_SHARED) }
    }

    fun clearAll() {
        prefs.edit { clear() }
    }

    /**
     * Adopt another sensor's stored session into this store — used on staging→production promotion so
     * the production driver durably resumes the promoted sensor after a restart. Copies identity / MAC
     * / device name / KEKS key + its [sessionStartMs], and clears the ingest high-water mark (the
     * promoted sensor has a fresh EGV sequence space, so no old floor must survive).
     */
    fun adopt(session: OnePlusStoredSession, sessionStartMs: Long) {
        prefs.edit {
            putString(KEY_PIN, session.identity.pin)
            putString(KEY_SERIAL, session.identity.serial)
            putString(KEY_GTIN, session.identity.gtin)
            putString(KEY_RAW_GS1, session.identity.rawGs1)
            remove(KEY_LAST_SEQ)
            remove(KEY_LAST_TS)
            session.lastMac?.let { putString(KEY_MAC, it) }
            session.lastDeviceName?.let { putString(KEY_DEVICE_NAME, it) }
            session.sharedKey?.let { putString(KEY_SHARED, Base64.encode(it)) }
            if (sessionStartMs > 0L) {
                putLong(KEY_SESSION_START, sessionStartMs)
                // Keep the start owned by the promoted sensor's MAC, so re-connecting it later is
                // seen as the same sensor and does not restart the age clock (startSessionForSensor).
                session.lastMac?.let { putString(KEY_SESSION_START_MAC, it.uppercase()) }
            }
        }
        OnePlusLog.i("${OnePlusLogMarkers.SESSION}: adopted promoted sensor serial=${maskSerial(session.identity.serial)}")
    }

    companion object {

        /**
         * The sensor serial for a log line: only its last three characters, enough to tell two
         * sensors apart in a log without writing the whole serial into it.
         */
        fun maskSerial(serial: String?): String =
            when {
                serial.isNullOrBlank() -> "-"
                serial.length <= 3     -> "***"
                else                   -> "***" + serial.takeLast(3)
            }

        /**
         * Pure decision behind [startSessionForSensor]: does an explicit start of [deviceAddress]
         * begin a NEW sensor session?
         *
         * @param storedStartMs stored session start, 0 when none
         * @param storedOwnerMac MAC owning the stored start; null for a session anchored on the first
         *   reading before that key existed, which then falls back to [previousMac] instead of being
         *   read as another sensor (that would reset the age of a sensor that never changed)
         * @param previousMac last stored MAC of the running session, taken before this start
         */
        fun startsNewSession(
            storedStartMs: Long,
            storedOwnerMac: String?,
            previousMac: String?,
            deviceAddress: String,
        ): Boolean {
            if (storedStartMs <= 0L) return true
            val owner = storedOwnerMac?.takeIf { it.isNotBlank() }
                ?: previousMac?.takeIf { it.isNotBlank() }
                ?: return true
            return !owner.equals(deviceAddress, ignoreCase = true)
        }

        /**
         * Name of the slot's own file.
         *
         * Per-slot isolation: the STAGING sensor must not share identity / MAC / KEKS key / ingest
         * markers with PRODUCTION. A null or blank [namespace] gives the original single-sensor
         * file, so an install that predates the staging slot keeps its stored sensor.
         */
        fun prefsFileName(namespace: String?): String =
            if (namespace.isNullOrBlank()) PREFS_NAME else "${PREFS_NAME}_$namespace"

        private const val PREFS_NAME = "dexcom_oneplus_sensor"
        private const val KEY_PIN = "pin"
        private const val KEY_SERIAL = "serial"
        private const val KEY_GTIN = "gtin"
        private const val KEY_RAW_GS1 = "raw_gs1"
        private const val KEY_MAC = "last_mac"
        private const val KEY_DEVICE_NAME = "last_device_name"
        private const val KEY_SHARED = "shared_key_b64"
        private const val KEY_LAST_SEQ = "last_ingest_seq"
        private const val KEY_LAST_TS = "last_ingest_ts"
        private const val KEY_SESSION_START = "session_start_ms"

        /** MAC that owns [KEY_SESSION_START] — tells "same sensor re-connected" from "new sensor". */
        private const val KEY_SESSION_START_MAC = "session_start_mac"
        private const val KEY_SLOT_PRESENT = "slot_present"
        private const val KEY_SLOT_EGV_COUNT = "slot_valid_egv_count"
        private const val KEY_SLOT_WARMUP_DONE = "slot_warmup_done"
    }
}
