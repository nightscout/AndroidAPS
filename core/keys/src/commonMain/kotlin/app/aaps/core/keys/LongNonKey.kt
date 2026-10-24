package app.aaps.core.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class LongNonKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = true
) : LongNonPreferenceKey {

    LocalProfileLastChange("local_profile_last_change", 0L),

    BtWatchdogLastBark("bt_watchdog_last", 0L),
    /**
     * When this phone registered its current pump. NOT exportable, and it is the third of the trio
     * with `StringNonKey.ActivePumpType` and `ActivePumpSerialNumber` - see the note there.
     *
     * It is the "accept nothing older than this" line for pump history: `confirmActivePump` rejects
     * records stamped before it. Another phone's value would silently drop a stretch of this pump's
     * history, or accept a stretch that belongs to a different one.
     */
    ActivePumpChangeTimestamp("active_pump_change_timestamp", 0L, exportable = false),
    LastCleanupRun("last_cleanup_run", 0L),
    /**
     * The time before which the last automatic cleanup deleted records. `PeriodicMaintenance` lets
     * it move forward only a little per pass, so a forward clock jump cannot delete the whole
     * history at once (#5210).
     */
    LastCleanupCutoff("last_cleanup_cutoff", 0L),

    // NSCv3 client-control pairing (excluded from export — replay protection regresses if restored)
    NsClientControlCounterSent("nsclient_control_counter_sent", 0L, exportable = false),

    // When this client paired with master. Used by OrphanDetector to suppress false-positive
    // orphan signals on settings/aaps docs whose srvModified predates the pairing (master
    // hasn't republished the roster yet). Not exported — re-pair regenerates this.
    NsClientControlPairedAt("nsclient_control_paired_at", 0L, exportable = false),
    // When the client sent the hello of its last key exchange. No new one is started for a while after it, so the
    // master's answer is not overtaken by a second exchange (see ClientPairingRepository.needsKeyExchange).
    NsClientControlKeyExchangeAt("nsclient_control_key_exchange_at", 0L, exportable = false),
    // Master: the "signed at" of its last signed settings doc; the next is always later, also after the clock went back.
    NsClientControlSignedAt("nsclient_control_signed_at", 0L, exportable = false),
    // Client: the "signed at" of the last cold and hot doc it took. An older one is a replay and is refused.
    NsClientControlColdSignedAt("nsclient_control_cold_signed_at", 0L, exportable = false),
    NsClientControlHotSignedAt("nsclient_control_hot_signed_at", 0L, exportable = false),
    LastVacuumRun("last_vacuum_run", 0L),
}

