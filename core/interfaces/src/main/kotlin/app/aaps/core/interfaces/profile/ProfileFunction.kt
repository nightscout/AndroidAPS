package app.aaps.core.interfaces.profile

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.PS
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import kotlinx.coroutines.flow.StateFlow

interface ProfileFunction {

    /**
     * Profile name with added modifiers
     */
    suspend fun getProfileName(): String

    /**
     * Profile name without any modifiers
     */
    suspend fun getOriginalProfileName(): String

    /**
     * Profile name with added modifiers and remaining time
     */
    suspend fun getProfileNameWithRemainingTime(): String

    /**
     * Check if there is actual profile existing
     */
    suspend fun isProfileValid(from: String): Boolean

    /**
     * User preferences unit set in preferences
     */
    fun getUnits(): GlucoseUnit

    /**
     * Get effective (active) profile confirmed by pump for "now"
     */
    suspend fun getProfile(): EffectiveProfile?

    /**
     * Get effective (active) profile confirmed by pump for time
     */
    suspend fun getProfile(time: Long): EffectiveProfile?

    /**
     * Get requested profile by user (profile must not be active yet)
     *
     * @return ProfileSwitch if exists
     */
    suspend fun getRequestedProfile(): PS?

    /**
     * Get requested profile by user (profile must not be active yet)
     *
     * @return true if ProfileSwitch != EffectiveProfileSwitch
     */
    suspend fun isProfileChangePending(): Boolean

    /**
     * Build a new circadian profile switch request based on provided profile
     *
     * @param profileStore  ProfileStore to use
     * @param profileName   this profile from profile store
     * @param durationInMinutes
     * @param percentage        100 = no modification
     * @param timeShiftInHours  0 = no modification
     * @param timestamp         expected time
     * @return null if profile cannot be created from profile store
     */
    fun buildProfileSwitch(profileStore: ProfileStore, profileName: String, durationInMinutes: Int, percentage: Int, timeShiftInHours: Int, timestamp: Long, iCfg: ICfg): PS?

    /**
     * Create a new circadian profile switch request based on provided profile
     *
     * @param profileStore  ProfileStore to use
     * @param profileName   this profile from profile store
     * @param durationInMinutes
     * @param percentage        100 = no modification
     * @param timeShiftInHours  0 = no modification
     * @param timestamp         expected time
     * @param action Action for UserEntry logging
     * @param source Source for UserEntry logging
     * @param note Note for UserEntry logging
     * @param listValues Values for UserEntry logging
     * @return true if profile was created from store
     */
    suspend fun createProfileSwitch(
        profileStore: ProfileStore, profileName: String, durationInMinutes: Int, percentage: Int, timeShiftInHours: Int, timestamp: Long,
        action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>, iCfg: ICfg
    ): PS?

    /**
     * Create a new circadian profile switch request based on currently selected profile interface and default profile
     *
     * @param durationInMinutes
     * @param percentage        100 = no modification
     * @param timeShiftInHours  0 = no modification
     * @param action Action for UserEntry logging
     * @param source Source for UserEntry logging
     * @param note Note for UserEntry logging
     * @param listValues Values for UserEntry logging
     * @return true if profile switch is created
     */
    suspend fun createProfileSwitch(
        durationInMinutes: Int, percentage: Int, timeShiftInHours: Int,
        action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>
    ): PS?

    /**
     * Re-apply the currently active profile switch with a different insulin configuration.
     * Preserves the original profile name, percentage, timeshift, and remaining duration.
     *
     * @param iCfg new insulin configuration to apply
     * @param source Source for UserEntry logging
     * @return true if profile switch was created
     */
    suspend fun createProfileSwitchWithNewInsulin(iCfg: ICfg, source: Sources): Boolean

    /**
     * Insulin configuration currently in force, resolved in precedence order:
     *  1. the running [EffectiveProfile] — authoritative
     *  2. a [PS] the user has requested that has not become effective yet — still a real user choice
     *
     * Both tiers are values the user actually selected: insulin is chosen when filling/priming the pump and
     * carried into the resulting profile switch. There is deliberately **no** third tier — the insulin list
     * is a catalogue to choose from, never a source of "the current one" — so a null result means the caller
     * must ask the user or refuse the action, and must not substitute.
     *
     * A profile carrying the DB v33 migration sentinel counts as *not* having one — see [ICfg.isUsable]. A
     * record whose DIA rounds to zero is not a value the caller can act on, and letting it through here is
     * what turns a bad migration into a loop that aborts every cycle.
     *
     * @return the in-force [ICfg], or null when no profile is running, none is pending, or what they carry
     *   is unusable
     */
    suspend fun getRunningOrRequestedICfg(): ICfg? =
        getProfile()?.iCfg?.takeIf { it.isUsable } ?: getRequestedProfile()?.iCfg?.takeIf { it.isUsable }

    /**
     * Drop every cached effective profile and rebuild the synchronous [runningICfg] mirror from the DB.
     *
     * Needed after a bulk DB rewrite that does not flow through the normal per-row [observeChanges] path —
     * specifically the v33 insulin-configuration repair, which UPDATEs the running effectiveProfileSwitch
     * row in place. getProfile() canonicalizes each EPS by id, so the pre-repair copy (carrying the
     * `insulinEndTime = -1` sentinel, DIA 0.0) stays pinned for the whole session and the APS aborts every
     * cycle on the DIA hard limit. Calling this right after the repair makes the *current* session re-read
     * the healed rows instead of only self-healing on the next app start or profile switch.
     */
    suspend fun invalidateCache()

    /**
     * Synchronous mirror of the running profile's insulin, for the few callers that cannot suspend —
     * non-suspend property getters and Compose composition. Kept current by the same effective-profile
     * subscription that invalidates the profile cache, so it can never lag behind [getProfile].
     *
     * Narrower than [getRunningOrRequestedICfg]: this reflects the running [EffectiveProfile] only and does
     * not see a requested-but-not-yet-effective switch. **Use [getRunningOrRequestedICfg] wherever a
     * coroutine is available**; reach for this only when the call site genuinely cannot suspend.
     *
     * `null` carries the same meaning as there — nothing is in force, so the caller must ask the user or
     * refuse, never substitute a catalogue entry.
     */
    val runningICfg: StateFlow<ICfg?>
}