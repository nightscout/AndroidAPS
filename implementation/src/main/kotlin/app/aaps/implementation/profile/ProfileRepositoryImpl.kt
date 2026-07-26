package app.aaps.implementation.profile

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileErrorType
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profile.ProfileValidationError
import app.aaps.core.interfaces.profile.PureProfile
import app.aaps.core.interfaces.profile.SingleProfile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.ProfileComposedBooleanKey
import app.aaps.core.keys.ProfileComposedStringKey
import app.aaps.core.keys.ProfileIntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.blockFromJsonArray
import app.aaps.core.objects.extensions.toPureProfile
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.R
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Single source of truth for the local profile list.
 *
 * Holds the mutable [profilesList] (backed by SharedPreferences) and exposes immutable
 * snapshots via the [profiles] and [profile] StateFlows. All mutations are serialised
 * through [mutex]; readers consume the StateFlows lock-free.
 *
 * Profile JSON ([profile]) is recomputed from [profilesList] after every mutation and is
 * the canonical name → profile-data lookup used by:
 *  - [ProfileFunction] for the active-profile path
 *  - Pump activation wizards (Omnipod/Equil/Eopatch/Medtrum)
 *  - Nightscout / Xdrip sync uploads
 *
 * Exceptions from JSON / preferences I/O are surfaced via [Result.failure] so callers can
 * render them as UI feedback rather than receiving an uncaught coroutine failure.
 */
@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
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
) : ProfileRepository {

    private val mutex = Mutex()

    // Mutable storage. Mutated only inside [mutex.withLock] — or in [init] before any
    // other coroutine can observe. Never expose directly; callers see snapshots via the
    // StateFlows below.
    private var profilesList: ArrayList<SingleProfile> = ArrayList()
    private var rawProfile: ProfileStore? = null

    private val _profiles = MutableStateFlow<List<SingleProfile>>(emptyList())
    override val profiles: StateFlow<List<SingleProfile>> = _profiles.asStateFlow()

    private val _profile = MutableStateFlow<ProfileStore?>(null)
    override val profile: StateFlow<ProfileStore?> = _profile.asStateFlow()

    init {
        // Synchronous initial load. Dagger constructs this @Singleton before any other
        // coroutine can run, so no mutex is needed here. Once init returns, the StateFlow
        // initial values reflect persisted state.
        //
        // Do NOT move this off the calling thread — snapshot() must complete before any
        // coroutine subscriber can observe the StateFlows, otherwise collectors would see
        // the empty defaults before the persisted state appears.
        loadSettingsInternal()
        snapshot()
    }

    /**
     * Snapshot the mutable storage into the StateFlows. Always called at the end of a
     * mutation, inside the mutex. Reads `profilesList` / `rawProfile` (which are stable
     * under the lock) and publishes immutable views.
     *
     * Order matters: `_profile` is written first, then `_profiles`. At least one current
     * subscriber (ProfileManagementViewModel in the UI module) reads `profile.value` inside
     * a `profiles` collector — writing the by-name JSON projection first means that when
     * the list emit fires, the store the collector reads is the new one (or newer), never
     * older. Subscribers that read only one flow are unaffected by ordering.
     */
    private fun snapshot() {
        _profile.value = rawProfile
        _profiles.value = profilesList.toList()
    }

    // ---------------------------------------------------------------------------------------------
    // Mutations — all serialised through the mutex. Each ends with [snapshot] so subscribers
    // see the post-mutation state on the next collect.
    // ---------------------------------------------------------------------------------------------

    override suspend fun clone(index: Int): Result<Unit> = mutex.withLock {
        if (index !in profilesList.indices) {
            return@withLock Result.failure(IndexOutOfBoundsException("clone($index): list size ${profilesList.size}"))
        }
        runCatching {
            withContext(Dispatchers.IO) {
                val p = profilesList[index].deepClone()
                p.name += " copy"
                profilesList.add(p)
                storeSettingsInternal(timestamp = dateUtil.now())
            }
            snapshot()
        }
    }

    override suspend fun remove(index: Int): Result<Unit> = mutex.withLock {
        if (index !in profilesList.indices) {
            return@withLock Result.failure(IndexOutOfBoundsException("remove($index): list size ${profilesList.size}"))
        }
        runCatching {
            withContext(Dispatchers.IO) {
                profilesList.removeAt(index)
                // Invariant: at least one profile always exists. If the user removed the last
                // one, fabricate a default to fill the slot.
                if (profilesList.isEmpty()) addNewProfileInternal()
                storeSettingsInternal(timestamp = dateUtil.now())
            }
            snapshot()
        }
    }

    override suspend fun add(profile: SingleProfile): Result<Unit> = mutex.withLock {
        runCatching {
            withContext(Dispatchers.IO) {
                profilesList.add(profile)
                storeSettingsInternal(timestamp = dateUtil.now())
            }
            snapshot()
        }
    }

    override suspend fun replace(index: Int, profile: SingleProfile): Result<Unit> = mutex.withLock {
        if (index !in profilesList.indices) {
            return@withLock Result.failure(IndexOutOfBoundsException("replace($index): list size ${profilesList.size}"))
        }
        runCatching {
            withContext(Dispatchers.IO) {
                // Defensive clone: the caller may continue to hold the reference (the editor
                // keeps editingProfile alive after save). Storing the live reference would let
                // subsequent caller-side mutations leak directly into profilesList[index].
                profilesList[index] = profile.deepClone()
                storeSettingsInternal(timestamp = dateUtil.now())
            }
            snapshot()
        }
    }

    override suspend fun reset(): Result<Unit> = mutex.withLock {
        runCatching {
            withContext(Dispatchers.IO) {
                loadSettingsInternal()
            }
            snapshot()
        }
    }

    override suspend fun loadFromNs(store: ProfileStore): Result<Unit> = mutex.withLock {
        runCatching {
            withContext(Dispatchers.IO) {
                loadFromStoreInternal(store)
            }
            snapshot()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Non-suspend pure helpers — safe to call from any context, no mutex needed.
    // ---------------------------------------------------------------------------------------------

    override fun validateStructured(profile: SingleProfile): List<ProfileValidationError> {
        // Pure CPU function of the passed-in profile + DI-injected helpers (HardLimits,
        // pump description, etc.) that are safe to read concurrently.
        val errors = mutableListOf<ProfileValidationError>()
        with(profile) {
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
                if (blockFromJsonArray(basal, dateUtil)?.all { it.amount < 0.01 || it.amount > hardLimits.maxBasal() } != false) {
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
                if (blockFromJsonArray(basal, dateUtil)?.all { it.amount < 0.01 || it.amount > hardLimits.maxBasal() } != false) {
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

    override fun validatePumpCompatibility(profile: SingleProfile, percentage: Int): List<ProfileValidationError> {
        // Pump-dependent, non-blocking check: is this profile's basal deliverable by the active pump
        // at [percentage]? Only the basal block is pump-specific (min/max rate, 30-min granularity).
        val pure = profile.toPureProfile(dateUtil) ?: return emptyList()
        val sealed = ProfileSealed.Pure(pure, activePlugin)
        sealed.pct = percentage
        val check = sealed.validatePump("pumpCompatibility", activePlugin.activePump, config, rh, notificationManager, false)
        return if (check.isValid) emptyList()
        else listOf(ProfileValidationError(ProfileErrorType.BASAL, rh.gs(R.string.profile_basal_not_compatible_with_pump)))
    }

    override fun newDraft(): SingleProfile {
        // Empty (zero) blocks → the profile is semantically invalid, so the editor's Save stays
        // disabled until the user fills it in. Nothing is persisted here; the editor commits via [add].
        val existingNames = profiles.value.mapTo(HashSet()) { it.name }
        val free = (1..10000).firstOrNull { Constants.LOCAL_PROFILE + it !in existingNames } ?: 0
        return SingleProfile(
            name = Constants.LOCAL_PROFILE + free,
            mgdl = profileFunction.get().getUnits() == GlucoseUnit.MGDL,
            ic = singleBlockProfileArray(0.0),
            isf = singleBlockProfileArray(0.0),
            basal = singleBlockProfileArray(0.0),
            targetLow = singleBlockProfileArray(0.0),
            targetHigh = singleBlockProfileArray(0.0)
        )
    }

    override fun copyFrom(pureProfile: PureProfile, newName: String): SingleProfile {
        // Reads the published [profile] StateFlow snapshot rather than the mutable
        // [rawProfile] field, so it doesn't need the mutex. TOCTOU on the name-uniqueness
        // check is harmless: at worst, two concurrent copies of the same name end up
        // timestamp-suffixed.
        var verifiedName = newName
        if (_profile.value?.getSpecificProfile(newName) != null) {
            verifiedName += " " + dateUtil.now().toString()
        }
        val profile = ProfileSealed.Pure(pureProfile, activePlugin)
        val pureJson = pureProfile.jsonObject
        return SingleProfile(
            name = verifiedName,
            mgdl = profile.units == GlucoseUnit.MGDL,
            ic = pureJson.getJSONArray("carbratio"),
            isf = pureJson.getJSONArray("sens"),
            basal = pureJson.getJSONArray("basal"),
            targetLow = pureJson.getJSONArray("target_low"),
            targetHigh = pureJson.getJSONArray("target_high")
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Internal helpers — assume the mutex is already held (or that this is [init]).
    // ---------------------------------------------------------------------------------------------

    private fun loadSettingsInternal() {
        val n = preferences.get(ProfileIntKey.AmountOfProfiles)
        profilesList.clear()
        for (i in 0 until n) {
            val name = preferences.get(ProfileComposedStringKey.LocalProfileNumberedName, i)
            if (profilesList.any { it.name == name }) continue
            try {
                profilesList.add(
                    SingleProfile(
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
        createAndStoreConvertedProfile()
    }

    private fun storeSettingsInternal(timestamp: Long) {
        val n = profilesList.size
        for (i in 0 until n) {
            profilesList[i].run {
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedName, i, value = name)
                preferences.put(ProfileComposedBooleanKey.LocalProfileNumberedMgdl, i, value = mgdl)
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedIc, i, value = ic.toString())
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedIsf, i, value = isf.toString())
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedBasal, i, value = basal.toString())
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedTargetLow, i, value = targetLow.toString())
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedTargetHigh, i, value = targetHigh.toString())
            }
        }
        preferences.put(ProfileIntKey.AmountOfProfiles, n)
        preferences.put(LongNonKey.LocalProfileLastChange, timestamp)
        createAndStoreConvertedProfile()
        aapsLogger.debug(LTag.PROFILE, "Storing settings: " + rawProfile?.getData().toString())
    }

    private fun loadFromStoreInternal(store: ProfileStore) {
        try {
            val newProfiles: ArrayList<SingleProfile> = ArrayList()
            for (p in store.getProfileList()) {
                val pureProfile = store.getSpecificProfile(p.toString())
                val validityCheck = pureProfile?.let {
                    // Accept by semantic validity only — a profile that's merely incompatible with the
                    // current pump is still valid data to store; pump compatibility is enforced at activation.
                    ProfileSealed.Pure(it, activePlugin).validateSemantic(rh, hardLimits)
                } ?: Profile.ValidityCheck()
                if (pureProfile != null && validityCheck.isValid) {
                    // copyFrom would timestamp-suffix the name if it collides with the
                    // CURRENT store, but here we're REPLACING the whole list — the NS name
                    // should be preserved verbatim. Reuse copyFrom for the JSON-field
                    // unpacking, then restore the raw name.
                    val sp = copyFrom(pureProfile, p.toString())
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
                profilesList = newProfiles
                aapsLogger.debug(LTag.PROFILE, "Accepted ${profilesList.size} profiles")
                storeSettingsInternal(timestamp = store.getStartDate())
            } else {
                aapsLogger.debug(LTag.PROFILE, "ProfileStore not accepted")
            }
        } catch (e: Exception) {
            aapsLogger.error("Error loading ProfileStore", e)
        }
    }

    private fun addNewProfileInternal() {
        val existingNames = profilesList.mapTo(HashSet()) { it.name }
        val free = (1..10000).firstOrNull { Constants.LOCAL_PROFILE + it !in existingNames } ?: 0
        val isMgdl = profileFunction.get().getUnits() == GlucoseUnit.MGDL
        // Seed a new profile with conservative, hard-limit-valid placeholders rather than zeros, so a
        // freshly added profile is valid out of the box. A zero-seeded profile is semantically invalid
        // and would silently block the whole profile-store sync until edited (see #4872). ISF and the
        // targets are stored in the profile's glucose unit, so they branch on [isMgdl].
        profilesList.add(
            SingleProfile(
                name = Constants.LOCAL_PROFILE + free,
                mgdl = isMgdl,
                ic = singleBlockProfileArray(15.0),
                isf = singleBlockProfileArray(if (isMgdl) 100.0 else 5.6),
                basal = singleBlockProfileArray(0.1),
                targetLow = singleBlockProfileArray(if (isMgdl) 110.0 else 6.1),
                targetHigh = singleBlockProfileArray(if (isMgdl) 120.0 else 6.7)
            )
        )
    }

    /** A single 00:00 profile block carrying [value], matching the JSON shape AAPS profiles expect. */
    private fun singleBlockProfileArray(value: Double): JSONArray =
        JSONArray().put(
            JSONObject()
                .put("time", "00:00")
                .put("timeAsSeconds", 0)
                .put("value", value)
        )

    private fun createAndStoreConvertedProfile() {
        val json = JSONObject()
        val store = JSONObject()
        try {
            for (i in profilesList.indices) {
                profilesList[i].run {
                    val pj = JSONObject()
                    pj.put("carbratio", ic)
                    pj.put("sens", isf)
                    pj.put("basal", basal)
                    pj.put("target_low", targetLow)
                    pj.put("target_high", targetHigh)
                    pj.put("units", if (mgdl) GlucoseUnit.MGDL.asText else GlucoseUnit.MMOL.asText)
                    pj.put("timezone", TimeZone.getDefault().id)
                    store.put(name, pj)
                }
            }
            // First profile is the stable "default" for the serialised store. Activation
            // decisions live in ProfileFunction (EPS-based); this field is purely cosmetic
            // for NS upload and tooling that reads the store JSON directly.
            if (profilesList.isNotEmpty()) json.put("defaultProfile", profilesList.first().name)
            val startDate = preferences.getIfExists(LongNonKey.LocalProfileLastChange) ?: dateUtil.now()
            json.put("date", startDate)
            json.put("created_at", dateUtil.toISOAsUTC(startDate))
            json.put("startDate", dateUtil.toISOAsUTC(startDate))
            json.put("store", store)
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
        rawProfile = profileStoreProvider.get().with(json)
    }
}
