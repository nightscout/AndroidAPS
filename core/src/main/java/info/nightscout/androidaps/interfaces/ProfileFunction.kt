package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.database.entities.ProfileSwitch

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
    fun getProfile(): Profile?

    /**
     * Get effective (active) profile confirmed by pump for time
     */
    fun getProfile(time: Long): Profile?

    /**
     * Get requested profile by user (profile must not be active yet)
     *
     * @return ProfileSwitch if exists
     */
    fun getRequestedProfile(): ProfileSwitch?

    /**
     * Create a new circadian profile switch request based on provided profile
     *
     * @param profileStore  ProfileStore to use
     * @param profileName   this profile from profile store
     * @param durationInMinutes
     * @param percentage        100 = no modification
     * @param timeShiftInHours  0 = no modification
     * @param timestamp         expected time
     */
    fun createProfileSwitch(profileStore: ProfileStore, profileName: String, durationInMinutes: Int, percentage: Int, timeShiftInHours: Int, timestamp: Long)

    /**
     * Create a new circadian profile switch request based on currently selected profile interface and default profile
     *
     * @param durationInMinutes
     * @param percentage        100 = no modification
     * @param timeShiftInHours  0 = no modification
     */
    fun createProfileSwitch(durationInMinutes: Int, percentage: Int, timeShiftInHours: Int)

    /*
     * Midnight time conversion
     * (here as well for easy mock)
     */
    fun secondsFromMidnight(): Int = Profile.secondsFromMidnight()
    fun secondsFromMidnight(date: Long): Int = Profile.secondsFromMidnight(date)
}