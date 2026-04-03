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
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.ProfileComposedBooleanKey
import app.aaps.core.keys.ProfileComposedDoubleKey
import app.aaps.core.keys.ProfileComposedStringKey
import app.aaps.core.keys.ProfileIntKey
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

    override val profiles: List<LocalProfileManager.SingleProfile>
        get() = _profiles.toList()

    override val numOfProfiles: Int
        get() = _profiles.size

    override var currentProfileIndex: Int = 0

    override var isEdited: Boolean = false

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
        val numOfProfiles = preferences.get(ProfileIntKey.AmountOfProfiles)
        _profiles.clear()

        for (i in 0 until numOfProfiles) {
            val name = preferences.get(ProfileComposedStringKey.LocalProfileNumberedName, i)
            if (isExistingName(name)) continue
            try {
                _profiles.add(
                    LocalProfileManager.SingleProfile(
                        name = name,
                        mgdl = preferences.get(ProfileComposedBooleanKey.LocalProfileNumberedMgdl, i),
                        ic = JSONArray(preferences.get(ProfileComposedStringKey.LocalProfileNumberedIc, i)),
                        isf = JSONArray(preferences.get(ProfileComposedStringKey.LocalProfileNumberedIsf, i)),
                        basal = JSONArray(preferences.get(ProfileComposedStringKey.LocalProfileNumberedBasal, i)),
                        targetLow = JSONArray(preferences.get(ProfileComposedStringKey.LocalProfileNumberedTargetLow, i)),
                        targetHigh = JSONArray(preferences.get(ProfileComposedStringKey.LocalProfileNumberedTargetHigh, i))
                    )
                )
            } catch (e: JSONException) {
                aapsLogger.error("Exception", e)
            }
        }
        isEdited = false
        createAndStoreConvertedProfile()
    }

    @Synchronized
    override fun storeSettings(timestamp: Long) {
        for (i in 0 until numOfProfiles) {
            _profiles[i].run {
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedName, i, value = name)
                preferences.put(ProfileComposedBooleanKey.LocalProfileNumberedMgdl, i, value = mgdl)
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedIc, i, value = ic.toString())
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedIsf, i, value = isf.toString())
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedBasal, i, value = basal.toString())
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedTargetLow, i, value = targetLow.toString())
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedTargetHigh, i, value = targetHigh.toString())
            }
        }
        preferences.put(ProfileIntKey.AmountOfProfiles, numOfProfiles)
        preferences.put(LongNonKey.LocalProfileLastChange, timestamp)
        createAndStoreConvertedProfile()
        isEdited = false
        aapsLogger.debug(LTag.PROFILE, "Storing settings: " + rawProfile?.getData().toString())
        rxBus.send(EventProfileStoreChanged())
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
