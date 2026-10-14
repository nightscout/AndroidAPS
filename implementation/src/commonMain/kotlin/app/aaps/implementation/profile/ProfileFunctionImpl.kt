package app.aaps.implementation.profile

import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.PS
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.CoreUiStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class ProfileFunctionImpl(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val rh: TextResolver,
    private val activePlugin: ActivePlugin,
    private val profileRepository: ProfileRepository,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val hardLimits: HardLimits,
    private val notificationManager: NotificationManager,
    private val appScope: CoroutineScope
) : ProfileFunction {

    // Both maps are guarded by [cacheLock]. They were a ConcurrentHashMap and a HashMap under a
    // `synchronized(cache)` monitor; every access was already inside that monitor, so one lock over
    // plain maps is the same thing without the JVM only types.
    val cacheLock = AapsLock()
    val cache = mutableMapOf<Long, EffectiveProfile>()

    // Dedup for the per-second [cache]. getEffectiveProfileSwitchActiveAt() deep-copies a brand-new EPS
    // (fresh block lists + Block objects, see EffectiveProfileSwitchExtension.fromDb) on every call, so
    // without this every distinct second queried would retain its OWN full copy of the very same running
    // profile — up to 30000 identical copies within one switch's window. Canonicalize by EPS id: the first
    // deep copy seen for an id is kept and every later second reuses it, so the whole window shares one
    // instance. The wrapper stays per-second (cheap, re-captures activePlugin.activeAPS exactly as before).
    // Guarded by the [cache] monitor. Safe to share because Block and TargetBlock are immutable and
    // the accessors derive fresh lists via shiftBlock, so a shared profile cannot be altered by a
    // reader. This used to rest on a convention instead - validatePump() clamped basal amounts in
    // place, and sharing was only safe because that clamp happened to run on freshly built profiles.
    // The clamp is gone and the types are `val`, so the guarantee is now the compiler's.
    val canonicalEps = mutableMapOf<Long, EPS>()

    private val _runningICfg = MutableStateFlow<ICfg?>(null)
    override val runningICfg: StateFlow<ICfg?> = _runningICfg.asStateFlow()

    init {
        // Populate the mirror off the main thread so the synchronous readers never block on the DB.
        appScope.launch { _runningICfg.value = readRunningICfg() }
        persistenceLayer.observeChanges(EPS::class)
            .collectResilient(appScope, aapsLogger, LTag.PROFILE) { epsList ->
                epsList.minOfOrNull { it.timestamp }?.let { timestamp ->
                    // Cache keys are rounded down to the second (see getProfile), so compare against
                    // the rounded timestamp with >= to also invalidate the entry for the second in
                    // which the change happened. A strict > against the raw ms timestamp would leave
                    // the current-second entry stale and getProfile() would keep returning the old EPS.
                    val rounded = timestamp - timestamp % 1000
                    cacheLock.withLock {
                        cache.keys.removeAll { key -> key >= rounded }
                        // An edited EPS keeps its id but changes content, so a stale canonical copy would be
                        // re-served on the next miss. Clear the whole map (not just changed ids): it rebuilds
                        // lazily and any surviving older wrapper already holds its own reference — nothing dangles.
                        canonicalEps.clear()
                    }
                }
                // Refresh the mirror in the SAME subscription, after the invalidation above — a separate
                // collector on this flow has no ordering guarantee against it and could re-read the stale
                // cache, pinning the mirror to the previous insulin until the next profile change.
                _runningICfg.value = readRunningICfg()
            }
    }

    /**
     * The insulin of the running profile, read straight from the database.
     *
     * Not through [getProfile], because that answers null until app init completes, and the pump needs
     * the concentration before that: `PumpWithConcentrationImpl` falls back to 1.0 without it, which is
     * wrong for any other insulin. This is the same value `ProfileSealed.EPS` exposes as `iCfg`.
     */
    private suspend fun readRunningICfg(): ICfg? =
        persistenceLayer.getEffectiveProfileSwitchActiveAt(dateUtil.now())?.iCfg?.takeIf { it.isUsable }

    // Same rule as the interface default, but the running insulin is read from the database rather than
    // through getProfile(): the data migrations in MainApp ask for it before app init completes, and with
    // a null profile there they would stamp old records with a substitute insulin.
    override suspend fun getRunningOrRequestedICfg(): ICfg? =
        readRunningICfg() ?: getRequestedProfile()?.iCfg?.takeIf { it.isUsable }

    override suspend fun getProfileName(): String =
        getProfileName(dateUtil.now(), customized = true, showRemainingTime = false)

    override suspend fun getOriginalProfileName(): String =
        getProfileName(dateUtil.now(), customized = false, showRemainingTime = false)

    override suspend fun getProfileNameWithRemainingTime(): String =
        getProfileName(dateUtil.now(), customized = true, showRemainingTime = true)

    private suspend fun getProfileName(time: Long, customized: Boolean, showRemainingTime: Boolean): String {
        var profileName = rh.gs(CoreUiStrings.no_profile_set)

        val profileSwitch = persistenceLayer.getEffectiveProfileSwitchActiveAt(time)
        if (profileSwitch != null) {
            profileName = if (customized) profileSwitch.originalCustomizedName else profileSwitch.originalProfileName
            if (showRemainingTime && profileSwitch.originalDuration != 0L) {
                profileName += dateUtil.untilString(profileSwitch.originalEnd, rh)
            }
        }
        return profileName
    }

    override suspend fun isProfileValid(from: String): Boolean = getProfile() != null

    override suspend fun invalidateCache() {
        // Same invalidation the observeChanges(EPS) subscription performs, but driven explicitly: the v33
        // repair rewrites the running EPS row via a path that does not re-emit here, so without this the
        // stale canonical copy (DIA 0.0 sentinel) stays pinned for the whole session. Clear both maps, then
        // rebuild the synchronous mirror from the now-healed DB (after the clear, so it can't re-read stale).
        cacheLock.withLock {
            cache.clear()
            canonicalEps.clear()
        }
        _runningICfg.value = readRunningICfg()
    }

    override suspend fun getProfile(): EffectiveProfile? =
        getProfile(dateUtil.now())

    override suspend fun getProfile(time: Long): EffectiveProfile? {
        // No profile until app init completes, and none while a settings import rebuilds plugin state.
        // Until then activePlugin.activeAPS is null, and ProfileSealed takes the APS when it is built, so
        // a profile from that time keeps aps = null for good. The cache below then handed it out after
        // init too, and a wizard calculation with it crashed on "APS not defined" at start.
        if (!config.appInitialized) return null
        val rounded = time - time % 1000
        // Clear cache after longer use
        cacheLock.withLock {
            if (cache.keys.size > 30000) {
                cache.clear()
                canonicalEps.clear()
                aapsLogger.debug("Profile cache cleared")
            }
            if (cache.containsKey(rounded)) {
                return cache[rounded]
            }
        }
        val ps = persistenceLayer.getEffectiveProfileSwitchActiveAt(time)
        if (ps != null) {
            cacheLock.withLock {
                // Reuse the first deep copy seen for this EPS id; the throwaway copy from this DB read is
                // dropped (GC'd) when a canonical entry already exists, so the window keeps a single copy.
                val shared = canonicalEps.getOrPut(ps.id) { ps }
                val sealed = ProfileSealed.EPS(shared, activePlugin)
                cache.put(rounded, sealed)
                return sealed
            }
        }
        /*
        // Commented out because it's not possible to simply take Pure profile
        // because we don't know Insulin configuration for it

        // In NSClient mode effective profile may not be received if older than 2 days
        // Try to get it from device status
        // Remove this code after switch to api v3
        // ps == null
        if (config.AAPSCLIENT) {
            processedDeviceStatusData.pumpData?.activeProfileName?.let { activeProfile ->
                profileRepository.profile.value?.getSpecificProfile(activeProfile)?.let { ap ->
                    val sealed = ProfileSealed.Pure(ap, activePlugin)
                    cacheLock.withLock {
                        cache.put(rounded, sealed)
                    }
                    return sealed
                }

            }
        }
        */
        cacheLock.withLock {
            cache.remove(rounded)
        }
        return null
    }

    override suspend fun getRequestedProfile(): PS? = persistenceLayer.getProfileSwitchActiveAt(dateUtil.now())

    override suspend fun isProfileChangePending(): Boolean {
        val requested = getRequestedProfile() ?: return false
        val running = getProfile() ?: return true
        return !ProfileSealed.PS(requested, activePlugin).isEqual(running)
    }

    override fun getUnits(): GlucoseUnit =
        if (preferences.get(StringKey.GeneralUnits) == GlucoseUnit.MGDL.asText) GlucoseUnit.MGDL
        else GlucoseUnit.MMOL

    override fun buildProfileSwitch(profileStore: ProfileStore, profileName: String, durationInMinutes: Int, percentage: Int, timeShiftInHours: Int, timestamp: Long, iCfg: ICfg): PS? {
        val pureProfile = profileStore.getSpecificProfile(profileName) ?: return null
        return PS(
            timestamp = timestamp,
            basalBlocks = pureProfile.basalBlocks,
            isfBlocks = pureProfile.isfBlocks,
            icBlocks = pureProfile.icBlocks,
            targetBlocks = pureProfile.targetBlocks,
            glucoseUnit = pureProfile.glucoseUnit,
            profileName = profileName,
            timeshift = T.hours(timeShiftInHours.toLong()).msecs(),
            percentage = percentage,
            duration = T.mins(durationInMinutes.toLong()).msecs(),
            iCfg = iCfg
        )
    }

    override suspend fun createProfileSwitch(
        profileStore: ProfileStore, profileName: String, durationInMinutes: Int, percentage: Int, timeShiftInHours: Int, timestamp: Long,
        action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>, iCfg: ICfg
    ): PS? {
        val ps = buildProfileSwitch(profileStore, profileName, durationInMinutes, percentage, timeShiftInHours, timestamp, iCfg) ?: return null
        refusal(ps)?.let {
            aapsLogger.warn(LTag.PROFILE, "Profile switch to $profileName refused: $it")
            return null
        }
        val result = persistenceLayer.insertOrUpdateProfileSwitch(ps, action, source, note, listValues)
        return result.inserted.firstOrNull() ?: result.updated.firstOrNull()
    }

    override suspend fun createProfileSwitch(
        durationInMinutes: Int, percentage: Int, timeShiftInHours: Int,
        action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>, timestamp: Long
    ): PS? {
        val ps = activeProfileSwitch(durationInMinutes, percentage, timeShiftInHours, timestamp) { aapsLogger.warn(LTag.PROFILE, "Profile switch refused: $it") }
            ?: return null
        refusal(ps)?.let {
            aapsLogger.warn(LTag.PROFILE, "Profile switch to ${ps.profileName} refused: $it")
            return null
        }
        val result = persistenceLayer.insertOrUpdateProfileSwitch(ps, action, source, note, listValues)
        return result.inserted.firstOrNull() ?: result.updated.firstOrNull()
    }

    override suspend fun profileSwitchRefusal(profileStore: ProfileStore, profileName: String, durationInMinutes: Int, percentage: Int, timeShiftInHours: Int, iCfg: ICfg): String? {
        val ps = buildProfileSwitch(profileStore, profileName, durationInMinutes, percentage, timeShiftInHours, dateUtil.now(), iCfg)
            ?: return rh.gs(CoreUiStrings.notexists)
        return refusal(ps)
    }

    override suspend fun profileSwitchRefusal(durationInMinutes: Int, percentage: Int, timeShiftInHours: Int, timestamp: Long): String? {
        var whyNot: String? = null
        val ps = activeProfileSwitch(durationInMinutes, percentage, timeShiftInHours, timestamp) { whyNot = it } ?: return whyNot
        return refusal(ps)
    }

    /**
     * A switch at [timestamp] (0 means now) of the profile in force at that time to these values. A back-dated
     * switch so changes the profile that was really running then. When there is none to change, [whyNot] gets
     * the sentence for the user, and the result is null.
     */
    private suspend fun activeProfileSwitch(durationInMinutes: Int, percentage: Int, timeShiftInHours: Int, timestamp: Long, whyNot: (String) -> Unit): PS? {
        val at = timestamp.takeIf { it > 0L } ?: dateUtil.now()
        val profile = persistenceLayer.getPermanentProfileSwitchActiveAt(at)
        if (profile == null) {
            whyNot(rh.gs(CoreUiStrings.no_profile_set))
            return null
        }
        val profileStore = profileRepository.profile.value
        if (profileStore == null) {
            whyNot(rh.gs(CoreUiStrings.noprofile))
            return null
        }
        val ps = buildProfileSwitch(profileStore, profile.profileName, durationInMinutes, percentage, timeShiftInHours, at, profile.iCfg)
        if (ps == null) whyNot(rh.gs(CoreUiStrings.scene_profile_not_found, profile.profileName))
        return ps
    }

    /**
     * Why [ps] cannot be activated, or null when it can. The pump first, with a sentence for each cause the
     * check reports. Its own reasons are not used: they say a value "was replaced", from the time the check
     * still changed values, and that is not true any more. Then the hard limits, which say which value is
     * out of range.
     */
    private fun refusal(ps: PS): String? {
        val profile = ProfileSealed.PS(ps, activePlugin)
        val from = rh.gs(CoreUiStrings.careportal_profileswitch)
        val pumpCheck = profile.validatePump(from, activePlugin.activePump, config, rh, notificationManager, false)
        if (!pumpCheck.isValid) return when (pumpCheck.basalProblem) {
            Profile.BasalProblem.NOT_ALIGNED_TO_HOURS -> rh.gs(InterfacesStrings.basalprofilenotaligned, from)
            Profile.BasalProblem.BELOW_PUMP_MINIMUM   -> rh.gs(CoreUiStrings.profile_basal_below_pump_minimum)
            Profile.BasalProblem.ABOVE_PUMP_MAXIMUM,
            null                                      -> rh.gs(CoreUiStrings.profile_basal_not_compatible_with_pump)
        }
        val semantic = profile.validateSemantic(rh, hardLimits)
        if (!semantic.isValid) return semantic.reasons.firstOrNull() ?: rh.gs(CoreUiStrings.invalid_profile)
        return null
    }

    override suspend fun createProfileSwitchWithNewInsulin(iCfg: ICfg, source: Sources): Boolean {
        val switch = newInsulinSwitch { aapsLogger.warn(LTag.PROFILE, "Insulin switch to ${iCfg.insulinLabel} refused: $it") } ?: return false
        return createProfileSwitch(
            profileStore = switch.profileStore,
            profileName = switch.profileName,
            durationInMinutes = switch.durationMinutes,
            percentage = switch.percentage,
            timeShiftInHours = switch.timeshiftHours,
            timestamp = switch.timestamp,
            action = Action.PROFILE_SWITCH,
            source = source,
            listValues = listOfNotNull(
                ValueWithUnit.SimpleString(switch.profileName),
                ValueWithUnit.SimpleString(iCfg.insulinLabel),
                ValueWithUnit.Percent(switch.percentage),
                ValueWithUnit.Hour(switch.timeshiftHours).takeIf { switch.timeshiftHours != 0 },
                ValueWithUnit.Minute(switch.durationMinutes).takeIf { switch.durationMinutes != 0 }
            ),
            iCfg = iCfg
        ) != null
    }

    override suspend fun insulinSwitchRefusal(iCfg: ICfg): String? {
        var whyNot: String? = null
        val switch = newInsulinSwitch { whyNot = it } ?: return whyNot
        return profileSwitchRefusal(switch.profileStore, switch.profileName, switch.durationMinutes, switch.percentage, switch.timeshiftHours, iCfg)
    }

    /** The running profile switch with only its insulin changed: the same name, percentage, time shift and remaining time. */
    private class NewInsulinSwitch(
        val profileStore: ProfileStore,
        val profileName: String,
        val durationMinutes: Int,
        val percentage: Int,
        val timeshiftHours: Int,
        val timestamp: Long
    )

    /**
     * The values of the switch [createProfileSwitchWithNewInsulin] creates. When there is no profile to change,
     * [whyNot] gets the sentence for the user, and the result is null.
     *
     * The running profile first. Before it exists, the requested switch: a pump wizard switches the profile at
     * its gate, and on a new pump that switch is set only after the activation, when the wizard has already
     * switched the insulin. Without this the chosen insulin was not applied, and the wizard said "no profile set".
     */
    private suspend fun newInsulinSwitch(whyNot: (String) -> Unit): NewInsulinSwitch? {
        val current = (getProfile() as? ProfileSealed.EPS)?.value
            ?.let { CurrentSwitch(it.originalProfileName, it.originalPercentage, it.originalTimeshift, it.timestamp, it.originalDuration) }
            ?: getRequestedProfile()?.let { CurrentSwitch(it.profileName, it.percentage, it.timeshift, it.timestamp, it.duration) }
        if (current == null) {
            whyNot(rh.gs(CoreUiStrings.no_profile_set))
            return null
        }
        val profileStore = profileRepository.profile.value
        if (profileStore == null) {
            whyNot(rh.gs(CoreUiStrings.noprofile))
            return null
        }
        val now = dateUtil.now()
        val durationMinutes = if (current.duration > 0) {
            ((current.duration - (now - current.start)) / 60_000L).coerceAtLeast(0).toInt()
        } else 0
        return NewInsulinSwitch(
            profileStore = profileStore,
            profileName = current.profileName,
            durationMinutes = durationMinutes,
            percentage = current.percentage,
            timeshiftHours = T.msecs(current.timeshift).hours().toInt(),
            timestamp = now
        )
    }

    /** The switch whose insulin is changed. [timeshift] and [duration] in milliseconds, [start] when it began. */
    private class CurrentSwitch(val profileName: String, val percentage: Int, val timeshift: Long, val start: Long, val duration: Long)
}