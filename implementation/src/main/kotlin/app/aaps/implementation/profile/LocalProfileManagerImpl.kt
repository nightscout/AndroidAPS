package app.aaps.implementation.profile

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.LocalProfileManager
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileErrorType
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profile.ProfileValidationError
import app.aaps.core.interfaces.profile.PureProfile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventLocalProfileChanged
import app.aaps.core.interfaces.rx.events.EventProfileStoreChanged
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.blockFromJsonArray
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.R
import app.aaps.core.utils.JsonHelper
import dagger.Lazy
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class LocalProfileManagerImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val sp: SP,
    private val profileFunction: Lazy<ProfileFunction>,
    private val profileUtil: ProfileUtil,
    private val activePlugin: ActivePlugin,
    private val hardLimits: HardLimits,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val profileStoreProvider: Provider<ProfileStore>,
    private val notificationManager: NotificationManager
) : LocalProfileManager {

    private var rawProfile: ProfileStore? = null
    private var _profiles: ArrayList<LocalProfileManager.SingleProfile> = ArrayList()
    private val _legacyProfileNameToDia: MutableMap<String, Double> = mutableMapOf()

    override val legacyProfileNameToDia: Map<String, Double>
        get() = _legacyProfileNameToDia.toMap()

    override val profiles: List<LocalProfileManager.SingleProfile>
        get() = _profiles.toList()

    override val numOfProfiles: Int
        get() = _profiles.size

    override var currentProfileIndex: Int = 0

    override var isEdited: Boolean = false

    init {
        loadSettings()
    }

    override val profile: ProfileStore?
        get() = rawProfile

    override fun currentProfile(): LocalProfileManager.SingleProfile? =
        if (numOfProfiles > 0 && currentProfileIndex < numOfProfiles) _profiles[currentProfileIndex] else null

    @Synchronized
    override fun getEditedProfile(): PureProfile? {
        if (numOfProfiles == 0 || currentProfileIndex >= numOfProfiles) return null
        val profile = JSONObject()
        with(_profiles[currentProfileIndex]) {
            profile.put("carbratio", ic)
            profile.put("sens", isf)
            profile.put("basal", basal)
            profile.put("target_low", targetLow)
            profile.put("target_high", targetHigh)
            profile.put("units", if (mgdl) GlucoseUnit.MGDL.asText else GlucoseUnit.MMOL.asText)
            profile.put("timezone", TimeZone.getDefault().id)
        }
        val defaultUnits = JsonHelper.safeGetStringAllowNull(profile, "units", null)
        return pureProfileFromJson(profile, dateUtil, defaultUnits)
    }

    @Synchronized
    override fun validateProfile(): List<String> =
        validateProfileStructured().map { it.message }.distinct()

    @Synchronized
    override fun validateProfileStructured(): List<ProfileValidationError> {
        val errors = mutableListOf<ProfileValidationError>()
        if (numOfProfiles == 0 || currentProfileIndex >= numOfProfiles) {
            errors.add(ProfileValidationError(ProfileErrorType.GENERAL, rh.gs(R.string.no_profile_selected)))
            return errors
        }

        val pumpDescription = activePlugin.activePump.pumpDescription
        with(_profiles[currentProfileIndex]) {
            if (name.isEmpty()) {
                errors.add(ProfileValidationError(ProfileErrorType.NAME, rh.gs(R.string.missing_profile_name)))
            }
            if (blockFromJsonArray(ic, dateUtil)?.all { it.amount < hardLimits.minIC() || it.amount > hardLimits.maxIC() } != false) {
                errors.add(ProfileValidationError(ProfileErrorType.IC, rh.gs(R.string.error_in_ic_values)))
            }
            val low = blockFromJsonArray(targetLow, dateUtil)
            val high = blockFromJsonArray(targetHigh, dateUtil)
            if (mgdl) {
                if (blockFromJsonArray(isf, dateUtil)?.all { hardLimits.isInRange(it.amount, HardLimits.MIN_ISF, HardLimits.MAX_ISF) } == false) {
                    errors.add(ProfileValidationError(ProfileErrorType.ISF, rh.gs(R.string.error_in_isf_values)))
                }
                if (blockFromJsonArray(basal, dateUtil)?.all { it.amount < pumpDescription.basalMinimumRate || it.amount > 10.0 } != false) {
                    errors.add(ProfileValidationError(ProfileErrorType.BASAL, rh.gs(R.string.error_in_basal_values)))
                }
                if (low?.all { hardLimits.isInRange(it.amount, HardLimits.LIMIT_MIN_BG[0], HardLimits.LIMIT_MIN_BG[1]) } == false) {
                    errors.add(ProfileValidationError(ProfileErrorType.TARGET, rh.gs(R.string.error_in_target_values)))
                }
                if (high?.all { hardLimits.isInRange(it.amount, HardLimits.LIMIT_MAX_BG[0], HardLimits.LIMIT_MAX_BG[1]) } == false) {
                    errors.add(ProfileValidationError(ProfileErrorType.TARGET, rh.gs(R.string.error_in_target_values)))
                }
            } else {
                if (blockFromJsonArray(isf, dateUtil)?.all { hardLimits.isInRange(profileUtil.convertToMgdl(it.amount, GlucoseUnit.MMOL), HardLimits.MIN_ISF, HardLimits.MAX_ISF) } == false) {
                    errors.add(ProfileValidationError(ProfileErrorType.ISF, rh.gs(R.string.error_in_isf_values)))
                }
                if (blockFromJsonArray(basal, dateUtil)?.all { it.amount < pumpDescription.basalMinimumRate || it.amount > 10.0 } != false) {
                    errors.add(ProfileValidationError(ProfileErrorType.BASAL, rh.gs(R.string.error_in_basal_values)))
                }
                if (low?.all { hardLimits.isInRange(profileUtil.convertToMgdl(it.amount, GlucoseUnit.MMOL), HardLimits.LIMIT_MIN_BG[0], HardLimits.LIMIT_MIN_BG[1]) } == false) {
                    errors.add(ProfileValidationError(ProfileErrorType.TARGET, rh.gs(R.string.error_in_target_values)))
                }
                if (high?.all { hardLimits.isInRange(profileUtil.convertToMgdl(it.amount, GlucoseUnit.MMOL), HardLimits.LIMIT_MAX_BG[0], HardLimits.LIMIT_MAX_BG[1]) } == false) {
                    errors.add(ProfileValidationError(ProfileErrorType.TARGET, rh.gs(R.string.error_in_target_values)))
                }
            }
            low?.let { lowList ->
                high?.let { highList ->
                    for (i in lowList.indices) {
                        if (lowList[i].amount > highList[i].amount) {
                            errors.add(ProfileValidationError(ProfileErrorType.TARGET, rh.gs(R.string.error_in_target_values)))
                            break
                        }
                    }
                }
            }
            if (name.contains(".")) {
                errors.add(ProfileValidationError(ProfileErrorType.NAME, rh.gs(R.string.profile_name_contains_dot)))
            }
        }
        return errors.distinctBy { it.type to it.message }
    }

    @Synchronized
    override fun loadSettings() {
        _profiles.clear()

        // Try new single-JSON format first
        loadFromJson(preferences.get(StringNonKey.LocalProfileData))
        // Fall back to old composed keys if new format produced nothing
        if (_profiles.isEmpty() && sp.getInt(LEGACY_AMOUNT_OF_PROFILES_KEY, 0) > 0) {
            migrateFromComposedKeys()
        }
        // Fall back to ancient raw SP keys if still nothing
        if (_profiles.isEmpty() && hasLegacyRawSpProfileKeys()) {
            migrateFromRawSp()
        }
        isEdited = false
        createAndStoreConvertedProfile()
    }

    private fun loadFromJson(json: String) {
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                try {
                    val obj = array.getJSONObject(i)
                    val name = obj.getString("name")
                    if (isExistingName(name)) {
                        aapsLogger.warn(LTag.PROFILE, "Skipping duplicate profile name: $name")
                        continue
                    }
                    _profiles.add(
                        LocalProfileManager.SingleProfile(
                            name = name,
                            mgdl = obj.getBoolean("mgdl"),
                            ic = obj.getJSONArray("ic"),
                            isf = obj.getJSONArray("isf"),
                            basal = obj.getJSONArray("basal"),
                            targetLow = obj.getJSONArray("targetLow"),
                            targetHigh = obj.getJSONArray("targetHigh")
                        )
                    )
                } catch (e: JSONException) {
                    aapsLogger.error("Error loading profile $i, skipping", e)
                }
            }
        } catch (e: JSONException) {
            aapsLogger.error("Error parsing profiles JSON", e)
        }
    }

    // region Legacy migration — TODO: Remove once all users migrated from pre-JSON profile formats

    /**
     * Migrate from composed keys format (LocalProfile_isf_0, LocalProfile_ic_0, ...) to single JSON.
     * Uses raw SP access — these keys were never registered in the typed preference system after
     * the move to single-JSON storage.
     * TODO: Remove in future versions.
     */
    private fun migrateFromComposedKeys() {
        aapsLogger.info(LTag.PROFILE, "Migrating profiles from composed keys to single JSON")
        val numOfProfiles = sp.getInt(LEGACY_AMOUNT_OF_PROFILES_KEY, 0)
        for (i in 0 until numOfProfiles) {
            val name = sp.getString(LEGACY_COMPOSED_NAME_PREFIX + i, "")
            if (name.isEmpty() || isExistingName(name)) {
                aapsLogger.warn(LTag.PROFILE, "Skipping empty or duplicate profile name during migration: $name")
                continue
            }
            try {
                _profiles.add(
                    LocalProfileManager.SingleProfile(
                        name = name,
                        mgdl = sp.getBoolean(LEGACY_COMPOSED_MGDL_PREFIX + i, false),
                        ic = JSONArray(sp.getString(LEGACY_COMPOSED_IC_PREFIX + i, DEFAULT_PROFILE_ARRAY)),
                        isf = JSONArray(sp.getString(LEGACY_COMPOSED_ISF_PREFIX + i, DEFAULT_PROFILE_ARRAY)),
                        basal = JSONArray(sp.getString(LEGACY_COMPOSED_BASAL_PREFIX + i, DEFAULT_PROFILE_ARRAY)),
                        targetLow = JSONArray(sp.getString(LEGACY_COMPOSED_TARGET_LOW_PREFIX + i, DEFAULT_PROFILE_ARRAY)),
                        targetHigh = JSONArray(sp.getString(LEGACY_COMPOSED_TARGET_HIGH_PREFIX + i, DEFAULT_PROFILE_ARRAY))
                    )
                )
            } catch (e: JSONException) {
                aapsLogger.error("Exception migrating profile $i, skipping", e)
            }
        }
        // Write new format (even if empty, to prevent re-running migration) and clean up old keys
        preferences.put(StringNonKey.LocalProfileData, profilesToJson().toString())
        removeComposedKeys(numOfProfiles)
    }

    /**
     * TODO: Remove in future versions.
     */
    private fun removeComposedKeys(numOfProfiles: Int) {
        for (i in 0 until numOfProfiles) {
            sp.remove(LEGACY_COMPOSED_NAME_PREFIX + i)
            sp.remove(LEGACY_COMPOSED_MGDL_PREFIX + i)
            sp.remove(LEGACY_COMPOSED_IC_PREFIX + i)
            sp.remove(LEGACY_COMPOSED_ISF_PREFIX + i)
            sp.remove(LEGACY_COMPOSED_BASAL_PREFIX + i)
            sp.remove(LEGACY_COMPOSED_TARGET_LOW_PREFIX + i)
            sp.remove(LEGACY_COMPOSED_TARGET_HIGH_PREFIX + i)
        }
        sp.remove(LEGACY_AMOUNT_OF_PROFILES_KEY)
    }

    /**
     * Check whether the ancient raw SP format has any profile keys (e.g. LocalProfile_0_isf).
     * TODO: Remove in future versions.
     */
    private fun hasLegacyRawSpProfileKeys(): Boolean {
        val prefix = Constants.LOCAL_PROFILE + "_"
        return sp.getAll().keys.any { key ->
            key.startsWith(prefix) && (
                key.endsWith("_name") || key.endsWith("_mgdl") ||
                    key.endsWith("_isf") || key.endsWith("_ic") ||
                    key.endsWith("_basal") || key.endsWith("_targetlow") ||
                    key.endsWith("_targethigh") || key.endsWith("_dia")
                )
        }
    }

    /**
     * Migrate from ancient raw SharedPreferences format (LocalProfile_0_isf, LocalProfile_0_dia, ...)
     * directly to single JSON. Also extracts legacy DIA values into [legacyProfileNameToDia]
     * for the one-time ICfg database backfill in MainApp.
     * TODO: Remove in future versions.
     */
    private fun migrateFromRawSp() {
        aapsLogger.info(LTag.PROFILE, "Migrating profiles from ancient raw SP keys to single JSON")
        val prefix = Constants.LOCAL_PROFILE + "_"
        val indexToProfile = mutableMapOf<Int, JSONObject>()
        val indexToName = mutableMapOf<Int, String>()
        val indexToDia = mutableMapOf<Int, Double>()
        val keysToRemove = mutableListOf<String>()

        for ((key, value) in sp.getAll()) {
            if (!key.startsWith(prefix)) continue
            try {
                when {
                    key.endsWith("_name")       -> {
                        val idx = key.split("_")[1].toInt()
                        indexToName[idx] = value as String
                        indexToProfile.getOrPut(idx) { JSONObject() }.put("name", value)
                        keysToRemove.add(key)
                    }

                    key.endsWith("_mgdl")       -> {
                        val idx = key.split("_")[1].toInt()
                        indexToProfile.getOrPut(idx) { JSONObject() }.put("mgdl", value as Boolean)
                        keysToRemove.add(key)
                    }

                    key.endsWith("_isf")        -> {
                        val idx = key.split("_")[1].toInt()
                        indexToProfile.getOrPut(idx) { JSONObject() }.put("isf", JSONArray(value as String))
                        keysToRemove.add(key)
                    }

                    key.endsWith("_ic")         -> {
                        val idx = key.split("_")[1].toInt()
                        indexToProfile.getOrPut(idx) { JSONObject() }.put("ic", JSONArray(value as String))
                        keysToRemove.add(key)
                    }

                    key.endsWith("_basal")      -> {
                        val idx = key.split("_")[1].toInt()
                        indexToProfile.getOrPut(idx) { JSONObject() }.put("basal", JSONArray(value as String))
                        keysToRemove.add(key)
                    }

                    key.endsWith("_targetlow")  -> {
                        val idx = key.split("_")[1].toInt()
                        indexToProfile.getOrPut(idx) { JSONObject() }.put("targetLow", JSONArray(value as String))
                        keysToRemove.add(key)
                    }

                    key.endsWith("_targethigh") -> {
                        val idx = key.split("_")[1].toInt()
                        indexToProfile.getOrPut(idx) { JSONObject() }.put("targetHigh", JSONArray(value as String))
                        keysToRemove.add(key)
                    }

                    key.endsWith("_dia")        -> {
                        val idx = key.split("_")[1].toInt()
                        indexToDia[idx] = value.toString().toDouble()
                        keysToRemove.add(key)
                    }
                }
            } catch (e: Exception) {
                aapsLogger.error("Error migrating raw SP key $key", e)
            }
        }

        // Build profile list from reconstructed JSON objects
        indexToProfile.keys.sorted().forEach { idx ->
            val obj = indexToProfile[idx] ?: return@forEach
            try {
                val name = obj.getString("name")
                if (isExistingName(name)) return@forEach
                _profiles.add(
                    LocalProfileManager.SingleProfile(
                        name = name,
                        mgdl = obj.optBoolean("mgdl", true),
                        ic = obj.optJSONArray("ic") ?: JSONArray(Constants.DEFAULT_PROFILE_ARRAY),
                        isf = obj.optJSONArray("isf") ?: JSONArray(Constants.DEFAULT_PROFILE_ARRAY),
                        basal = obj.optJSONArray("basal") ?: JSONArray(Constants.DEFAULT_PROFILE_ARRAY),
                        targetLow = obj.optJSONArray("targetLow") ?: JSONArray(Constants.DEFAULT_PROFILE_ARRAY),
                        targetHigh = obj.optJSONArray("targetHigh") ?: JSONArray(Constants.DEFAULT_PROFILE_ARRAY)
                    )
                )
            } catch (e: JSONException) {
                aapsLogger.error("Error finalizing raw SP profile $idx, skipping", e)
            }
        }

        // Populate legacy dia map for MainApp ICfg backfill
        _legacyProfileNameToDia.clear()
        indexToDia.forEach { (idx, dia) ->
            indexToName[idx]?.let { name -> _legacyProfileNameToDia[name] = dia }
        }

        // Write new JSON format and delete all raw SP keys
        preferences.put(StringNonKey.LocalProfileData, profilesToJson().toString())
        keysToRemove.forEach { sp.remove(it) }
        // Remove legacy AmountOfProfiles key if present
        sp.remove(LEGACY_AMOUNT_OF_PROFILES_KEY)
    }

    private companion object {
        // Raw SP key constants for legacy profile storage formats.
        // TODO: Remove in future versions along with the migration methods above.
        private const val LEGACY_AMOUNT_OF_PROFILES_KEY = "LocalProfile_profiles"
        private const val LEGACY_COMPOSED_NAME_PREFIX = "LocalProfile_name_"
        private const val LEGACY_COMPOSED_MGDL_PREFIX = "LocalProfile_mgdl_"
        private const val LEGACY_COMPOSED_IC_PREFIX = "LocalProfile_ic_"
        private const val LEGACY_COMPOSED_ISF_PREFIX = "LocalProfile_isf_"
        private const val LEGACY_COMPOSED_BASAL_PREFIX = "LocalProfile_basal_"
        private const val LEGACY_COMPOSED_TARGET_LOW_PREFIX = "LocalProfile_targetlow_"
        private const val LEGACY_COMPOSED_TARGET_HIGH_PREFIX = "LocalProfile_targethigh_"
        private const val DEFAULT_PROFILE_ARRAY = "[]"
    }

    // endregion

    @Synchronized
    override fun storeSettings(timestamp: Long) {
        preferences.put(StringNonKey.LocalProfileData, profilesToJson().toString())
        preferences.put(LongNonKey.LocalProfileLastChange, timestamp)
        createAndStoreConvertedProfile()
        isEdited = false
        aapsLogger.debug(LTag.PROFILE, "Storing settings: " + rawProfile?.getData().toString())
        rxBus.send(EventProfileStoreChanged())
    }

    private fun profilesToJson(): JSONArray {
        val array = JSONArray()
        for (profile in _profiles) {
            val obj = JSONObject()
            obj.put("name", profile.name)
            obj.put("mgdl", profile.mgdl)
            obj.put("ic", profile.ic)
            obj.put("isf", profile.isf)
            obj.put("basal", profile.basal)
            obj.put("targetLow", profile.targetLow)
            obj.put("targetHigh", profile.targetHigh)
            array.put(obj)
        }
        return array
    }

    @Synchronized
    override fun loadFromStore(store: ProfileStore) {
        try {
            val newProfiles: ArrayList<LocalProfileManager.SingleProfile> = ArrayList()
            for (p in store.getProfileList()) {
                val profile = store.getSpecificProfile(p.toString())
                val validityCheck = profile?.let {
                    ProfileSealed.Pure(profile, activePlugin).isValid("NS", activePlugin.activePump, config, rh, notificationManager, hardLimits, false)
                } ?: Profile.ValidityCheck()
                if (profile != null && validityCheck.isValid) {
                    val sp = copyFrom(profile, p.toString())
                    sp.name = p.toString()
                    newProfiles.add(sp)
                } else {
                    notificationManager.post(
                        NotificationId.INVALID_PROFILE_NOT_ACCEPTED,
                        R.string.invalid_profile_not_accepted, p.toString()
                    )
                }
            }
            if (newProfiles.isNotEmpty()) {
                _profiles = newProfiles
                currentProfileIndex = 0
                isEdited = false
                aapsLogger.debug(LTag.PROFILE, "Accepted ${_profiles.size} profiles")
                storeSettings(timestamp = store.getStartDate())
                rxBus.send(EventLocalProfileChanged())
            } else {
                aapsLogger.debug(LTag.PROFILE, "ProfileStore not accepted")
            }
        } catch (e: Exception) {
            aapsLogger.error("Error loading ProfileStore", e)
        }
    }

    override fun copyFrom(pureProfile: PureProfile, newName: String): LocalProfileManager.SingleProfile {
        var verifiedName = newName
        if (rawProfile?.getSpecificProfile(newName) != null) {
            verifiedName += " " + dateUtil.now().toString()
        }
        val profile = ProfileSealed.Pure(pureProfile, activePlugin)
        val pureJson = pureProfile.jsonObject
        return LocalProfileManager.SingleProfile(
            name = verifiedName,
            mgdl = profile.units == GlucoseUnit.MGDL,
            ic = pureJson.getJSONArray("carbratio"),
            isf = pureJson.getJSONArray("sens"),
            basal = pureJson.getJSONArray("basal"),
            targetLow = pureJson.getJSONArray("target_low"),
            targetHigh = pureJson.getJSONArray("target_high")
        )
    }

    override fun addNewProfile() {
        var free = 0
        for (i in 1..10000) {
            if (rawProfile?.getSpecificProfile(Constants.LOCAL_PROFILE + i) == null) {
                free = i
                break
            }
        }
        _profiles.add(
            LocalProfileManager.SingleProfile(
                name = Constants.LOCAL_PROFILE + free,
                mgdl = profileFunction.get().getUnits() == GlucoseUnit.MGDL,
                ic = JSONArray(Constants.DEFAULT_PROFILE_ARRAY),
                isf = JSONArray(Constants.DEFAULT_PROFILE_ARRAY),
                basal = JSONArray(Constants.DEFAULT_PROFILE_ARRAY),
                targetLow = JSONArray(Constants.DEFAULT_PROFILE_ARRAY),
                targetHigh = JSONArray(Constants.DEFAULT_PROFILE_ARRAY)
            )
        )
        currentProfileIndex = _profiles.size - 1
        createAndStoreConvertedProfile()
        storeSettings(timestamp = 0)
    }

    override fun cloneProfile() {
        val p = _profiles[currentProfileIndex].deepClone()
        p.name += " copy"
        _profiles.add(p)
        currentProfileIndex = _profiles.size - 1
        createAndStoreConvertedProfile()
        storeSettings(timestamp = dateUtil.now())
        isEdited = false
    }

    override fun addProfile(profile: LocalProfileManager.SingleProfile) {
        _profiles.add(profile)
        currentProfileIndex = _profiles.size - 1
        createAndStoreConvertedProfile()
        storeSettings(timestamp = dateUtil.now())
        isEdited = false
    }

    override fun removeCurrentProfile() {
        _profiles.removeAt(currentProfileIndex)
        if (_profiles.isEmpty()) addNewProfile()
        currentProfileIndex = 0
        createAndStoreConvertedProfile()
        storeSettings(timestamp = dateUtil.now())
        isEdited = false
    }

    override fun notifyProfileChanged() {
        rxBus.send(EventLocalProfileChanged())
    }

    private fun isExistingName(name: String?): Boolean {
        for (p in _profiles) {
            if (p.name == name) return true
        }
        return false
    }

    private fun createAndStoreConvertedProfile() {
        rawProfile = createProfileStore()
    }

    private fun createProfileStore(): ProfileStore {
        val json = JSONObject()
        val store = JSONObject()

        try {
            for (i in 0 until numOfProfiles) {
                _profiles[i].run {
                    val profile = JSONObject()
                    profile.put("carbratio", ic)
                    profile.put("sens", isf)
                    profile.put("basal", basal)
                    profile.put("target_low", targetLow)
                    profile.put("target_high", targetHigh)
                    profile.put("units", if (mgdl) GlucoseUnit.MGDL.asText else GlucoseUnit.MMOL.asText)
                    profile.put("timezone", TimeZone.getDefault().id)
                    store.put(name, profile)
                }
            }
            if (numOfProfiles > 0) json.put("defaultProfile", currentProfile()?.name)
            val startDate = preferences.getIfExists(LongNonKey.LocalProfileLastChange) ?: dateUtil.now()
            json.put("date", startDate)
            json.put("created_at", dateUtil.toISOAsUTC(startDate))
            json.put("startDate", dateUtil.toISOAsUTC(startDate))
            json.put("store", store)
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }

        return profileStoreProvider.get().with(json)
    }
}
