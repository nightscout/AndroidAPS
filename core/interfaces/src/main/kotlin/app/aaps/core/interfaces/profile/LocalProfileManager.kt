package app.aaps.core.interfaces.profile

import org.json.JSONArray

/**
 * Interface for managing local profiles.
 * Provides methods for profile CRUD operations, persistence, and state management.
 *
 * This interface is used by the Compose UI for profile management.
 */
interface LocalProfileManager {

    /**
     * SingleProfile stores a name of a profile in addition to PureProfile
     */
    class SingleProfile(
        var name: String,
        var mgdl: Boolean,
        var ic: JSONArray,
        var isf: JSONArray,
        var basal: JSONArray,
        var targetLow: JSONArray,
        var targetHigh: JSONArray,
    ) {

        fun deepClone(): SingleProfile =
            SingleProfile(
                name = name,
                mgdl = mgdl,
                ic = JSONArray(ic.toString()),
                isf = JSONArray(isf.toString()),
                basal = JSONArray(basal.toString()),
                targetLow = JSONArray(targetLow.toString()),
                targetHigh = JSONArray(targetHigh.toString())
            )
    }

    /**
     * List of all profiles in the store.
     */
    val profiles: List<SingleProfile>

    /**
     * Number of profiles in the store.
     */
    val numOfProfiles: Int

    /**
     * Index of the currently selected profile.
     */
    var currentProfileIndex: Int

    /**
     * Whether the current profile has unsaved changes.
     */
    var isEdited: Boolean

    /**
     * The profile store containing all profiles.
     */
    val profile: ProfileStore?

    /**
     * Legacy profile name → DIA map extracted from ancient raw SharedPreferences format.
     * Only populated during first-launch migration from pre-ICfg versions. Empty on all
     * subsequent launches. Used by MainApp.dataMigrations() to backfill ICfg data on
     * historical ProfileSwitch / Bolus records.
     *
     * TODO: Remove once all users have migrated from pre-ICfg format.
     */
    val legacyProfileNameToDia: Map<String, Double>
        get() = emptyMap()

    /**
     * Get the currently selected profile.
     *
     * @return The current SingleProfile or null if no profiles exist
     */
    fun currentProfile(): SingleProfile?

    /**
     * Get the currently edited profile as a PureProfile.
     * Used for validation and activation.
     *
     * @return PureProfile of the current profile or null
     */
    fun getEditedProfile(): PureProfile?

    /**
     * Validate the current profile.
     * Returns list of validation error messages, empty if valid.
     *
     * @return List of error messages, empty if profile is valid
     * @deprecated Use [validateProfileStructured] for typed errors
     */
    fun validateProfile(): List<String>

    /**
     * Validate the current profile with structured error types.
     * Returns list of validation errors with type information, empty if valid.
     *
     * @return List of [ProfileValidationError], empty if profile is valid
     */
    fun validateProfileStructured(): List<ProfileValidationError>

    /**
     * Check if the current profile is valid.
     *
     * @return true if valid, false otherwise
     */
    fun isValid(): Boolean = validateProfileStructured().isEmpty()

    /**
     * Load profiles from SharedPreferences.
     */
    fun loadSettings()

    /**
     * Save profiles to SharedPreferences.
     *
     * @param timestamp Timestamp of the change
     */
    fun storeSettings(timestamp: Long)

    /**
     * Import profiles from a ProfileStore.
     * Validates profiles before importing.
     *
     * @param store ProfileStore to import from
     */
    fun loadFromStore(store: ProfileStore)

    /**
     * Create a SingleProfile from a PureProfile.
     *
     * @param pureProfile Source profile
     * @param newName Name for the new profile
     * @return New SingleProfile
     */
    fun copyFrom(pureProfile: PureProfile, newName: String): SingleProfile

    /**
     * Create a new empty profile with default values.
     * Selects the new profile as current.
     */
    fun addNewProfile()

    /**
     * Clone the current profile.
     * Creates a copy with " copy" appended to the name.
     * Selects the cloned profile as current.
     */
    fun cloneProfile()

    /**
     * Add an existing profile to the store.
     * Selects the added profile as current.
     *
     * @param profile Profile to add
     */
    fun addProfile(profile: SingleProfile)

    /**
     * Remove the currently selected profile.
     * If this was the last profile, creates a new default profile.
     * Selects index 0 after removal.
     */
    fun removeCurrentProfile()

    /**
     * Notify listeners that profile data has changed.
     * Sends EventLocalProfileChanged via RxBus.
     */
    fun notifyProfileChanged()
}
