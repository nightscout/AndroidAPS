package app.aaps.pump.ap550s.keys

import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey

/**
 * Boolean values the AP550 driver stores but does not show on its own preference screen.
 */
enum class Ap550BooleanNonKey(
    override val key: String,
    override val defaultValue: Boolean
) : BooleanNonPreferenceKey {

    /**
     * Emulate a temp basal above the base rate with an extended bolus.
     *
     * The key string is shared with the Dana drivers on purpose - this is the same switch, and the
     * history of this driver is a Dana RS port. It is a non-preference key here because the old
     * `pref_ap550.xml` had the switch commented out: the AP550 screen never offered it. The default
     * is false, which is what the old `sp.getBoolean("danar_useextended", false)` read.
     */
    UseExtended("danar_useextended", false),

    /**
     * The pump's basal profile does not match what AAPS wants it to be.
     *
     * `isThisProfileSet` returns true (meaning "already set, no need to write") when it finds the
     * pump profile equal to the requested one. But that answer comes from a cached profile the
     * driver only refreshes once an hour, so a profile the user changed on the pump by hand, or a
     * freshly bound pump, can look equal while it is not. This flag forces the write instead.
     *
     * The default is `true` on purpose: when the value is missing (fresh install, or right after
     * unpair) we do not know the pump yet, so the safe answer is "write it". It is cleared only
     * when a profile write to the pump succeeds.
     */
    BasalProfileMismatch("ap550_basal_profile_mismatch", true),

    /**
     * True when the pump should report temp basal as a percentage of the base rate rather than as
     * an absolute rate. The legacy string key is `Visualize_basal_percent`.
     *
     * The AP550SE only supports percent mode, so the driver flips this on once it sees an SE.
     * Defaults to `true`, matching the old `sp.getBoolean(key_ap550_basal_is_percent, true)`.
     */
    BasalIsPercent("Visualize_basal_percent", true),

    /** Whether the pump's log index has been fetched already. Legacy raw key "IsGetLogIndex". */
    IsGetLogIndex("IsGetLogIndex", false),

    /**
     * When true, the driver reads one additional temp-basal history entry right after a reconnect,
     * to catch the basal the pump ran while the link was down.
     */
    IsTempBasalRunningAfterDisconnect("is_temp_basal_running_after_disconnect", false),

    /** Whether the bolus/temp-basal log index has been fetched already. Legacy raw key. */
    IsGetBolusTempbasalLogIndex("IsGetBolusTempbasalLogIndex", false),

    /** Whether the pump's basal profile matches the one AAPS wants to write. */
    IsBasalSame("is_basal_same", false),
}