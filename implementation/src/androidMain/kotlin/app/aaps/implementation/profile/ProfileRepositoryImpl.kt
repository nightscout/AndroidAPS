package app.aaps.implementation.profile

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.di.ApplicationScope
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
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.objects.extensions.blockFromJsonArray
import app.aaps.core.objects.extensions.highToJSONArray
import app.aaps.core.objects.extensions.highToJsonArray
import app.aaps.core.objects.extensions.lowToJSONArray
import app.aaps.core.objects.extensions.lowToJsonArray
import app.aaps.core.objects.extensions.singleBlock
import app.aaps.core.objects.extensions.singleTargetBlock
import app.aaps.core.objects.extensions.targetBlockFromJsonArray
import app.aaps.core.objects.extensions.toJSONArray
import app.aaps.core.objects.extensions.toJsonArray
import app.aaps.core.objects.extensions.toPureProfile
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.R
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.TimeZone

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
// Metro annotations throughout, no javax and no Dagger. Dagger does not build this class any more -
// it gets the instance from a @Provides delegate in `:app` - so nothing here needs to be readable by
// Dagger, and Metro reads its own annotations without interop wherever the graph is generated.
//
// `Provider` replaces Dagger's `Lazy`: both defer, and ProfileFunction is a singleton, so the caching
// difference between them cannot be observed here.
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class ProfileRepositoryImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val profileFunction: Provider<ProfileFunction>,
    private val profileUtil: ProfileUtil,
    private val activePlugin: ActivePlugin,
    private val hardLimits: HardLimits,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val profileStoreProvider: Provider<ProfileStore>,
    private val notificationManager: NotificationManager,
    // Hosts the [StringNonKey.LocalProfileData] collector: the repository is a @Singleton that lives
    // as long as the process, so the collector must live that long too.
    @ApplicationScope private val appScope: CoroutineScope
) : ProfileRepository {

    private val mutex = Mutex()

    // Mutable storage. Mutated only inside [mutex.withLock] — or in [init] before any
    // other coroutine can observe. Never expose directly; callers see snapshots via the
    // StateFlows below.
    private var profilesList: ArrayList<SingleProfile> = ArrayList()
    private var rawProfile: ProfileStore? = null

    // Last payload this instance wrote to (or adopted from) [StringNonKey.LocalProfileData].
    // The preference observer fires for our OWN writes too, so it compares against this to tell
    // "somebody else changed the profiles" from "this is the echo of what I just stored".
    @Volatile private var lastKnownPayload: String? = null

    private val _profiles = MutableStateFlow<List<SingleProfile>>(emptyList())
    override val profiles: StateFlow<List<SingleProfile>> = _profiles.asStateFlow()

    private val _profile = MutableStateFlow<ProfileStore?>(null)
    override val profile: StateFlow<ProfileStore?> = _profile.asStateFlow()

    private val _revision = MutableStateFlow(0L)
    override val revision: StateFlow<Long> = _revision.asStateFlow()

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
        observeSyncedProfileData()
    }

    /**
     * Adopt profile lists that arrive from outside this instance.
     *
     * [StringNonKey.LocalProfileData] is `SyncSpec(Cold, Bidirectional)`, so on a paired client the
     * master's republished value lands via `putRemote`, and on a master a client's accepted edit lands
     * the same way. Neither goes through [storeSettingsInternal], so without this collector the
     * in-memory list and the StateFlows would keep serving the old profiles until the next restart.
     *
     * Own writes are filtered by content ([lastKnownPayload]) rather than by a flag: one save can
     * produce two emits (the local write, then the master's authoritative echo), so a one-shot flag
     * would be consumed by the first and let the second through as a foreign change.
     */
    private fun observeSyncedProfileData() {
        appScope.launch {
            preferences.observe(StringNonKey.LocalProfileData).drop(1).collect { payload ->
                if (payload.isEmpty() || payload == lastKnownPayload) return@collect
                mutex.withLock {
                    val parsed = parsePayload(payload) ?: return@withLock
                    aapsLogger.debug(LTag.PROFILE, "Adopting synced profile data, ${parsed.profiles.size} profiles")
                    profilesList = parsed.profiles
                    lastKnownPayload = payload
                    // Keep the NS gates in step: LocalProfileLastChange drives both the profile-store
                    // import check and the upload check, so an adopted list must move it too.
                    preferences.put(LongNonKey.LocalProfileLastChange, parsed.lastChange)
                    createAndStoreConvertedProfile()
                    snapshot()
                }
            }
        }
    }

    /**
     * Snapshot the mutable storage into the StateFlows. Always called at the end of a
     * mutation, inside the mutex. Reads `profilesList` / `rawProfile` (which are stable
     * under the lock) and publishes immutable views.
     *
     * Order matters: `_profile` is written first, then `_profiles`, and `_revision` last. At least
     * one current subscriber (ProfileManagementViewModel in the UI module) reads `profile.value`
     * inside a `profiles` collector — writing the by-name JSON projection first means that when
     * the list emit fires, the store the collector reads is the new one (or newer), never
     * older. Subscribers that read only one flow are unaffected by ordering.
     *
     * `_revision` goes last for the same reason one step further out: it is the "something happened"
     * signal, so by the time it fires both data flows must already carry the new state. It also
     * always emits, while `_profiles` deduplicates identical lists — see
     * [app.aaps.core.interfaces.profile.ProfileRepository.revision].
     */
    private fun snapshot() {
        _profile.value = rawProfile
        _profiles.value = profilesList.toList()
        _revision.value = _revision.value + 1
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
                val original = profilesList[index]
                profilesList.add(original.copy(name = original.name + " copy"))
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

    override suspend fun reorder(order: List<Int>): Result<Unit> = mutex.withLock {
        if (order.size != profilesList.size || order.toSet() != profilesList.indices.toSet()) {
            return@withLock Result.failure(
                IllegalArgumentException("reorder($order): not a permutation of 0..${profilesList.size - 1}")
            )
        }
        // Identity order — skip the write entirely. Persisting would bump LocalProfileLastChange and
        // trigger a full profile-store upload to NS/xDrip for a list that did not actually change.
        if (order.withIndex().all { (newIndex, oldIndex) -> newIndex == oldIndex }) {
            return@withLock Result.success(Unit)
        }
        runCatching {
            withContext(Dispatchers.IO) {
                // Rebuild rather than shuffle in place: storeSettingsInternal rewrites every indexed
                // preference key from scratch, so the new list only has to be correct, not minimal.
                profilesList = order.mapTo(ArrayList(order.size)) { profilesList[it] }
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
                // No defensive clone needed: SingleProfile is immutable, so the editor keeping its
                // reference alive after save cannot reach into profilesList.
                profilesList[index] = profile
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
            // Snapshot only when the store was actually taken. A rejected store changes nothing, and
            // [revision] means "a mutation happened" — bumping it anyway would tell the editor to
            // reload and would throw away whatever the user was typing at that moment.
            if (withContext(Dispatchers.IO) { loadFromStoreInternal(store) }) snapshot()
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
            // A schedule is invalid when it holds no block at all, or when *any* single block is out
            // of range. The "any" matters: checking only the first block would let one bad block hide
            // between good ones.
            fun invalid(blocks: List<Block>, inRange: (Double) -> Boolean): Boolean =
                blocks.isEmpty() || blocks.any { !inRange(it.amount) }

            // BG values are stored in the profile unit, the limits are always mg/dL.
            fun asMgdl(value: Double): Double = if (mgdl) value else profileUtil.convertToMgdl(value, GlucoseUnit.MMOL)

            fun targetError() = ProfileValidationError(ProfileErrorType.TARGET, rh.gs(R.string.error_in_target_values))

            if (invalid(ic) { it in hardLimits.icRange() }) {
                errors.add(ProfileValidationError(ProfileErrorType.IC, rh.gs(R.string.error_in_ic_values)))
            }
            if (invalid(isf) { asMgdl(it) in HardLimits.LIMIT_ISF }) {
                errors.add(ProfileValidationError(ProfileErrorType.ISF, rh.gs(R.string.error_in_isf_values)))
            }
            if (invalid(basal) { it in 0.01..hardLimits.maxBasal() }) {
                errors.add(ProfileValidationError(ProfileErrorType.BASAL, rh.gs(R.string.error_in_basal_values)))
            }
            if (target.isEmpty() || target.any { asMgdl(it.lowTarget) !in HardLimits.LIMIT_MIN_BG }) errors.add(targetError())
            if (target.isEmpty() || target.any { asMgdl(it.highTarget) !in HardLimits.LIMIT_MAX_BG }) errors.add(targetError())
            if (target.any { it.lowTarget > it.highTarget }) errors.add(targetError())
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
            mgdl = profileFunction().getUnits() == GlucoseUnit.MGDL,
            ic = singleBlock(0.0),
            isf = singleBlock(0.0),
            basal = singleBlock(0.0),
            target = singleTargetBlock(0.0, 0.0)
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
        // Straight field copy — a PureProfile already holds the same block lists, so there is no
        // JSON to unpack any more.
        return SingleProfile(
            name = verifiedName,
            mgdl = pureProfile.glucoseUnit == GlucoseUnit.MGDL,
            ic = pureProfile.icBlocks,
            isf = pureProfile.isfBlocks,
            basal = pureProfile.basalBlocks,
            target = pureProfile.targetBlocks
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Internal helpers — assume the mutex is already held (or that this is [init]).
    // ---------------------------------------------------------------------------------------------

    /**
     * Load the profile list, newest format first.
     *
     * 1. [StringNonKey.LocalProfileData] (the single JSON document) — used when its own `lastChange`
     *    is at least as new as [LongNonKey.LocalProfileLastChange];
     * 2. otherwise the legacy per-profile keys. They are also what a build older than this one writes,
     *    so a newer `LocalProfileLastChange` than the JSON carries means "an older build edited the
     *    profiles after we last wrote the JSON" (downgrade → edit → upgrade) and they win.
     *
     * The legacy keys are only ever read and re-converted, never deleted, so going back to an older
     * build keeps working.
     */
    private fun loadSettingsInternal() {
        profilesList.clear()
        val payload = preferences.get(StringNonKey.LocalProfileData)
        val parsed = parsePayload(payload)
        if (parsed != null && parsed.lastChange >= preferences.get(LongNonKey.LocalProfileLastChange)) {
            profilesList = parsed.profiles
            lastKnownPayload = payload
        }
        if (profilesList.isEmpty()) {
            loadFromLegacyKeysInternal()
            // Convert on the master only. A client never authors its profile list — it adopts the
            // master's over the sync channel or Nightscout's while unpaired — so converting there
            // would publish a stale local list back to the master.
            if (profilesList.isNotEmpty() && config.APS)
                storeSettingsInternal(preferences.get(LongNonKey.LocalProfileLastChange).takeIf { it > 0L } ?: dateUtil.now())
        }
        // Last resort: a document the stamp check rejected still beats showing no profiles at all.
        // A client that only ever received its list over the sync channel has no legacy keys to fall
        // back on, so without this a stamp that got ahead of the document would empty the screen.
        if (profilesList.isEmpty() && parsed != null) {
            profilesList = parsed.profiles
            lastKnownPayload = payload
        }
        createAndStoreConvertedProfile()
    }

    /** Read the pre-JSON per-profile keys. Kept for upgrade and for downgrade compatibility. */
    private fun loadFromLegacyKeysInternal() {
        val n = preferences.get(ProfileIntKey.AmountOfProfiles)
        for (i in 0 until n) {
            val name = preferences.get(ProfileComposedStringKey.LocalProfileNumberedName, i)
            if (profilesList.any { it.name == name }) continue
            try {
                val entry = JSONObject()
                    .put(KEY_IC, JSONArray(preferences.get(ProfileComposedStringKey.LocalProfileNumberedIc, i)))
                    .put(KEY_ISF, JSONArray(preferences.get(ProfileComposedStringKey.LocalProfileNumberedIsf, i)))
                    .put(KEY_BASAL, JSONArray(preferences.get(ProfileComposedStringKey.LocalProfileNumberedBasal, i)))
                    .put(KEY_TARGET_LOW, JSONArray(preferences.get(ProfileComposedStringKey.LocalProfileNumberedTargetLow, i)))
                    .put(KEY_TARGET_HIGH, JSONArray(preferences.get(ProfileComposedStringKey.LocalProfileNumberedTargetHigh, i)))
                profilesList.add(
                    SingleProfile(
                        name = name,
                        mgdl = preferences.get(ProfileComposedBooleanKey.LocalProfileNumberedMgdl, i),
                        ic = readSchedule(entry, KEY_IC, name),
                        isf = readSchedule(entry, KEY_ISF, name),
                        basal = readSchedule(entry, KEY_BASAL, name),
                        target = readTargetSchedule(entry, name)
                    )
                )
            } catch (e: JSONException) {
                aapsLogger.error("Exception", e)
            }
        }
    }

    /**
     * Where a stored profile list came from. It decides whether the write is announced to the sync
     * channel: [WriteOrigin.LOCAL] is this device's own edit and must travel, [WriteOrigin.ADOPTED] is
     * a list this device merely took over (Nightscout import) and must not be pushed back out.
     */
    private enum class WriteOrigin { LOCAL, ADOPTED }

    private fun storeSettingsInternal(timestamp: Long, origin: WriteOrigin = WriteOrigin.LOCAL) {
        // The legacy per-profile keys are intentionally NOT written any more. They stay behind,
        // frozen at their last value, purely so an older build can still read profiles after a
        // downgrade. LocalProfileLastChange is the tie-breaker between the two formats on load.
        val payload = profilesToJson(timestamp).toString()
        lastKnownPayload = payload
        when (origin) {
            // put() emits on syncedLocalChanges → the client publishes the edit to the master, and
            // on a master it triggers the cold republish that fans the list out to every client.
            WriteOrigin.LOCAL   -> preferences.put(StringNonKey.LocalProfileData, payload)
            // putRemote() stores without emitting — no echo back to where the list came from.
            WriteOrigin.ADOPTED -> preferences.putRemote(StringNonKey.LocalProfileData, payload, timestamp)
        }
        // Stamp last. If the process dies between the two writes, the document carries the newer
        // lastChange and still wins on load; stamping first would make the freshest document look like
        // the one an older build had overwritten.
        preferences.put(LongNonKey.LocalProfileLastChange, timestamp)
        createAndStoreConvertedProfile()
        aapsLogger.debug(LTag.PROFILE, "Storing settings ($origin): " + rawProfile?.getData().toString())
    }

    /**
     * The stored form: one document carrying its own edit stamp plus the profile list.
     *
     * The field names and the per-schedule array shape are the master↔client sync wire format and
     * the 3.4.x upgrade path, so they are rendered back exactly as before — the in-memory model
     * changed, the document did not. Targets are one paired list in memory and two arrays here.
     */
    private fun profilesToJson(timestamp: Long): JSONObject {
        val array = JSONArray()
        for (profile in profilesList) {
            array.put(
                JSONObject()
                    .put(KEY_NAME, profile.name)
                    .put(KEY_MGDL, profile.mgdl)
                    .put(KEY_IC, profile.ic.toJSONArray())
                    .put(KEY_ISF, profile.isf.toJSONArray())
                    .put(KEY_BASAL, profile.basal.toJSONArray())
                    .put(KEY_TARGET_LOW, profile.target.lowToJSONArray())
                    .put(KEY_TARGET_HIGH, profile.target.highToJSONArray())
            )
        }
        return JSONObject().put(KEY_LAST_CHANGE, timestamp).put(KEY_PROFILES, array)
    }

    private class ParsedProfiles(val lastChange: Long, val profiles: ArrayList<SingleProfile>)

    /**
     * Parse a stored/received document. Returns null only when the document itself is unusable
     * (not JSON, no profile array, no profiles left after parsing) — a single damaged FIELD falls back
     * to that field's default instead of dropping the whole profile, and a damaged ENTRY is skipped
     * rather than failing the document. Losing one profile must never look like "the user has none".
     */
    private fun parsePayload(payload: String): ParsedProfiles? {
        if (payload.isEmpty()) return null
        return try {
            val json = JSONObject(payload)
            val array = json.optJSONArray(KEY_PROFILES) ?: return null
            val parsed = ArrayList<SingleProfile>(array.length())
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i) ?: continue
                val name = entry.optString(KEY_NAME)
                if (name.isEmpty() || parsed.any { it.name == name }) continue
                parsed.add(
                    SingleProfile(
                        name = name,
                        // Same fallback as the pre-JSON key. It must NOT ask ProfileFunction for the
                        // current units: this runs from init(), and resolving that Lazy there closes a
                        // dependency cycle back into this repository (crash at startup).
                        mgdl = entry.optBoolean(KEY_MGDL, ProfileComposedBooleanKey.LocalProfileNumberedMgdl.defaultValue),
                        ic = readSchedule(entry, KEY_IC, name),
                        isf = readSchedule(entry, KEY_ISF, name),
                        basal = readSchedule(entry, KEY_BASAL, name),
                        target = readTargetSchedule(entry, name)
                    )
                )
            }
            if (parsed.isEmpty()) null else ParsedProfiles(json.optLong(KEY_LAST_CHANGE, 0L), parsed)
        } catch (e: JSONException) {
            aapsLogger.error(LTag.PROFILE, "Cannot parse stored profile data", e)
            null
        }
    }

    /**
     * One schedule out of a stored profile entry, or the zero placeholder when it cannot be read.
     *
     * "Cannot be read" now covers a damaged array as well as a missing one — with typed blocks the
     * parse either succeeds or it does not, where the old code could carry unreadable JSON around
     * untouched. The outcome is the documented one either way: a zero schedule is out of hard limits,
     * so the profile shows as invalid in the editor and is refused by save and by sync. It fails
     * where the user can see and fix it, instead of reaching the pump.
     */
    private fun readSchedule(entry: JSONObject, key: String, profileName: String): List<Block> =
        blockFromJsonArray(entry.optJSONArray(key), dateUtil) ?: run {
            if (entry.has(key)) aapsLogger.error(LTag.PROFILE, "Cannot read '$key' of profile '$profileName', using zero")
            singleBlock(0.0)
        }

    /** [readSchedule] for the paired target arrays. */
    private fun readTargetSchedule(entry: JSONObject, profileName: String): List<TargetBlock> =
        targetBlockFromJsonArray(entry.optJSONArray(KEY_TARGET_LOW), entry.optJSONArray(KEY_TARGET_HIGH), dateUtil) ?: run {
            if (entry.has(KEY_TARGET_LOW) || entry.has(KEY_TARGET_HIGH))
                aapsLogger.error(LTag.PROFILE, "Cannot read targets of profile '$profileName', using zero")
            singleTargetBlock(0.0, 0.0)
        }

    /** @return true when the incoming store replaced the list, false when it was rejected. */
    private fun loadFromStoreInternal(store: ProfileStore): Boolean {
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
                    // should be preserved verbatim. Reuse copyFrom for the field copying,
                    // then restore the raw name.
                    newProfiles.add(copyFrom(pureProfile, p.toString()).copy(name = p.toString()))
                } else {
                    notificationManager.post(
                        NotificationId.INVALID_PROFILE_NOT_ACCEPTED,
                        TextRef.AndroidRes(R.string.invalid_profile_not_accepted, listOf(p.toString())))
                }
            }
            if (newProfiles.isNotEmpty()) {
                profilesList = newProfiles
                aapsLogger.debug(LTag.PROFILE, "Accepted ${profilesList.size} profiles")
                // Adopted, not authored: a Nightscout store must not be pushed back to the master.
                storeSettingsInternal(timestamp = store.getStartDate(), origin = WriteOrigin.ADOPTED)
                return true
            }
            aapsLogger.debug(LTag.PROFILE, "ProfileStore not accepted")
        } catch (e: Exception) {
            aapsLogger.error("Error loading ProfileStore", e)
        }
        return false
    }

    private fun addNewProfileInternal() {
        val existingNames = profilesList.mapTo(HashSet()) { it.name }
        val free = (1..10000).firstOrNull { Constants.LOCAL_PROFILE + it !in existingNames } ?: 0
        val isMgdl = profileFunction().getUnits() == GlucoseUnit.MGDL
        // Seed a new profile with conservative, hard-limit-valid placeholders rather than zeros, so a
        // freshly added profile is valid out of the box. A zero-seeded profile is semantically invalid
        // and would silently block the whole profile-store sync until edited (see #4872). ISF and the
        // targets are stored in the profile's glucose unit, so they branch on [isMgdl].
        profilesList.add(
            SingleProfile(
                name = Constants.LOCAL_PROFILE + free,
                mgdl = isMgdl,
                ic = singleBlock(15.0),
                isf = singleBlock(if (isMgdl) 100.0 else 5.6),
                basal = singleBlock(0.1),
                target = singleTargetBlock(if (isMgdl) 110.0 else 6.1, if (isMgdl) 120.0 else 6.7)
            )
        )
    }

    /**
     * Rebuild the by-name store that [profile] publishes.
     *
     * Built as kotlinx directly rather than as `org.json` and converted: [ProfileStore] takes a
     * kotlinx tree now, and the block renderers already produce one, so the whole path from typed
     * blocks to the published store stays in one representation. There is no `try/catch` any more
     * because a builder cannot throw the way `JSONObject.put` could.
     */
    private fun createAndStoreConvertedProfile() {
        val startDate = preferences.getIfExists(LongNonKey.LocalProfileLastChange) ?: dateUtil.now()
        rawProfile = profileStoreProvider().with(
            buildJsonObject {
                // First profile is the stable "default" for the serialised store. Activation
                // decisions live in ProfileFunction (EPS-based); this field is purely cosmetic
                // for NS upload and tooling that reads the store JSON directly.
                if (profilesList.isNotEmpty()) put("defaultProfile", profilesList.first().name)
                put("date", startDate)
                put("created_at", dateUtil.toISOAsUTC(startDate))
                put("startDate", dateUtil.toISOAsUTC(startDate))
                putJsonObject("store") {
                    for (profile in profilesList) profile.run {
                        putJsonObject(name) {
                            put("carbratio", ic.toJsonArray())
                            put("sens", isf.toJsonArray())
                            put("basal", basal.toJsonArray())
                            put("target_low", target.lowToJsonArray())
                            put("target_high", target.highToJsonArray())
                            put("units", if (mgdl) GlucoseUnit.MGDL.asText else GlucoseUnit.MMOL.asText)
                            put("timezone", TimeZone.getDefault().id)
                        }
                    }
                }
            }
        )
    }

    companion object {

        // Field names of the stored document. They are part of the sync wire format between master
        // and client, so they must not be renamed once shipped.
        private const val KEY_LAST_CHANGE = "lastChange"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_NAME = "name"
        private const val KEY_MGDL = "mgdl"
        private const val KEY_IC = "ic"
        private const val KEY_ISF = "isf"
        private const val KEY_BASAL = "basal"
        private const val KEY_TARGET_LOW = "targetLow"
        private const val KEY_TARGET_HIGH = "targetHigh"
    }
}
