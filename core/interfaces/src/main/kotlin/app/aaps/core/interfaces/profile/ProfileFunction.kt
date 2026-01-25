package app.aaps.core.interfaces.profile

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.PS
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit

interface ProfileFunction {

    /**
     * Profile name with added modifiers
     */
    fun getProfileName(): String

    /**
     * Profile name without any modifiers
     */
    fun getOriginalProfileName(): String

    /**
     * Profile name with added modifiers and remaining time
     */
    fun getProfileNameWithRemainingTime(): String

    /**
     * Check if there is actual profile existing
     */
    fun isProfileValid(from: String): Boolean

    /**
     * User preferences unit set in preferences
     */
    fun getUnits(): GlucoseUnit

    /**
     * Get effective (active) profile confirmed by pump for "now"
     */
    fun getProfile(): EffectiveProfile?

    /**
     * Get effective (active) profile confirmed by pump for time
     */
    fun getProfile(time: Long): EffectiveProfile?

    /**
     * Get requested profile by user (profile must not be active yet)
     *
     * @return ProfileSwitch if exists
     */
    fun getRequestedProfile(): PS?

    /**
     * Get requested profile by user (profile must not be active yet)
     *
     * @return true if ProfileSwitch != EffectiveProfileSwitch
     */
    fun isProfileChangePending(): Boolean

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
    fun createProfileSwitch(
        profileStore: ProfileStore, profileName: String, durationInMinutes: Int, percentage: Int, timeShiftInHours: Int, timestamp: Long,
        action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>, iCfg: ICfg
    ): Boolean

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
    fun createProfileSwitch(
        durationInMinutes: Int, percentage: Int, timeShiftInHours: Int,
        action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>
    ): Boolean
}