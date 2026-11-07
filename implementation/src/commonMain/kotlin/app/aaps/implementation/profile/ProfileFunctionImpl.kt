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

    // Same rule as the interface default, requested first, but the running insulin is read from the database
    // rather than through getProfile(): the data migrations in MainApp ask for it before app init completes,
    // and with a null profile there they would stamp old records with a substitute insulin.
    override suspend fun getRunningOrRequestedICfg(): ICfg? =
        getRequestedProfile()?.iCfg?.takeIf { it.isUsable } ?: readRunningICfg()

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
                profileName = rh.gs(InterfacesStrings.value_with_detail, profileName, dateUtil.untilString(profileSwitch.originalEnd, rh, withParentheses = false))
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
        // On AAPSClient the running profile comes from the master: its originalPsId is the master's id, and the pump
        // there can have rounded the basal (#2454). The master has applied this PS when it wrote the EPS after it.
        if (config.AAPSCLIENT && running is ProfileSealed.EPS)
            return running.value.timestamp < requested.timestamp && !ProfileSealed.PS(requested, activePlugin).isEqual(running)
        return !ProfileSealed.PS(requested, activePlugin).isAppliedAs(running)
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
     * out of range. Then the insulin, unless [checkInsulin] is false: an insulin change gives all the
     * switches it writes the same new insulin, see [insulinChange].
     */
    private suspend fun refusal(ps: PS, checkInsulin: Boolean = true): String? {
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
        if (checkInsulin) insulinRefusal(ps)?.let { return it }
        return null
    }

    /**
     * A temporary switch must have the insulin of the permanent switch under it. The insulin is what is in the
     * pump. When only a temporary switch had the new one, the old insulin came back when it ended, and every
     * switch made from the running profile after that took the old insulin over (#5081). The name may differ,
     * the curve may not: the insulin list builds its names again at every start.
     */
    private suspend fun insulinRefusal(ps: PS): String? {
        if (ps.duration == 0L) return null
        val permanent = persistenceLayer.getPermanentProfileSwitchActiveAt(ps.timestamp)?.iCfg?.takeIf { it.isUsable } ?: return null
        if (ps.iCfg.hasSameCurve(permanent)) return null
        return rh.gs(CoreUiStrings.profile_switch_insulin_not_permanent, ps.iCfg.insulinLabel, permanent.insulinLabel)
    }

    override suspend fun createProfileSwitchWithNewInsulin(iCfg: ICfg, source: Sources): Boolean {
        val change = insulinChange(iCfg) { aapsLogger.warn(LTag.PROFILE, "Insulin switch to ${iCfg.insulinLabel} refused: $it") } ?: return false
        val shown = change.shown
        val timeshiftHours = T.msecs(shown.timeshift).hours().toInt()
        val durationMinutes = T.msecs(shown.duration).mins().toInt()
        val result = persistenceLayer.insertOrUpdateProfileSwitches(
            profileSwitches = change.switches,
            action = Action.PROFILE_SWITCH,
            source = source,
            listValues = listOfNotNull(
                ValueWithUnit.SimpleString(shown.profileName),
                ValueWithUnit.SimpleString(iCfg.insulinLabel),
                ValueWithUnit.Percent(shown.percentage),
                ValueWithUnit.Hour(timeshiftHours).takeIf { timeshiftHours != 0 },
                ValueWithUnit.Minute(durationMinutes).takeIf { durationMinutes != 0 }
            )
        )
        return result.inserted.isNotEmpty() || result.updated.isNotEmpty()
    }

    override suspend fun insulinSwitchRefusal(iCfg: ICfg): String? {
        var whyNot: String? = null
        insulinChange(iCfg) { whyNot = it }
        return whyNot
    }

    /** What an insulin change writes, in one transaction. [shown] is the switch in force after it, for the user entry. */
    private class InsulinChange(val switches: List<PS>, val shown: PS)

    /**
     * The switches that give the profile in force the insulin [iCfg]. When there is no profile to change, or one
     * of the switches would be refused, [whyNot] gets the sentence for the user, and the result is null.
     *
     * The insulin is what is in the pump, so it goes on the permanent switch. A temporary switch that is running
     * gets a copy with the new insulin, which ends when it would have ended. Before, only that copy was written,
     * and the old insulin came back when it ended (#5081). The copy is newer than the permanent switch, so it stays
     * in force, and each record has its own time in Nightscout. With less than a minute left the temporary switch
     * is not copied, and the permanent one takes over now. Switches set to start later get the new insulin too, or
     * they would bring the old one back.
     *
     * The new switches copy the records, only the insulin changes. They were built from the profile store before,
     * so a profile renamed since refused the whole insulin change, and changes saved in the store but never
     * activated became active with it.
     *
     * The switch in force is the requested one. It differs from the running profile only while the pump has not set
     * a newer switch yet, and a copy of the running one would undo that switch. A pump wizard on a new pump also
     * needs it: the gate switches the profile, but it runs only after the activation, when the wizard has already
     * switched the insulin. Only with no switch at all is the running profile used, built from the profile store.
     */
    private suspend fun insulinChange(iCfg: ICfg, whyNot: (String) -> Unit): InsulinChange? {
        val now = dateUtil.now()
        val requested = getRequestedProfile()
        val written = mutableListOf<PS>()
        if (requested != null) {
            val remaining = requested.timestamp + requested.duration - now
            val copyTemporary = requested.duration > 0 && remaining >= MIN_REMAINING_TO_COPY
            // With no permanent switch at all, a temporary one about to end becomes the permanent switch, as before.
            val permanent = if (requested.duration == 0L) requested
            else persistenceLayer.getPermanentProfileSwitchActiveAt(now) ?: requested.takeIf { !copyTemporary }
            permanent?.let { written += newSwitchLike(it, if (copyTemporary) permanentTime(requested.timestamp, now) else now, 0L, iCfg) }
            if (copyTemporary) written += newSwitchLike(requested, now, remaining, iCfg)
        } else {
            val running = (getProfile() as? ProfileSealed.EPS)?.value
            if (running == null) {
                whyNot(rh.gs(CoreUiStrings.no_profile_set))
                return null
            }
            val profileStore = profileRepository.profile.value
            if (profileStore == null) {
                whyNot(rh.gs(CoreUiStrings.noprofile))
                return null
            }
            // The running profile holds the basal with its percentage applied, so the switch is built from the store.
            val remaining = if (running.originalDuration > 0) running.timestamp + running.originalDuration - now else 0L
            written += buildProfileSwitch(
                profileStore, running.originalProfileName, 0, running.originalPercentage, T.msecs(running.originalTimeshift).hours().toInt(), now, iCfg
            )?.copy(duration = remaining.takeIf { it >= MIN_REMAINING_TO_COPY } ?: 0L)
                ?: run {
                    whyNot(rh.gs(CoreUiStrings.notexists))
                    return null
                }
        }
        val shown = written.last()
        written += persistenceLayer.getProfileSwitchesFromTime(now + 1, true)
            .filterNot { it.iCfg.isEqual(iCfg) }
            .map { it.copy(iCfg = iCfg) }
        for (switch in written) refusal(switch, checkInsulin = false)?.let {
            whyNot(it)
            return null
        }
        return InsulinChange(written, shown)
    }

    /**
     * When the permanent switch of an insulin change starts: one second before the copy of the temporary switch,
     * but after the temporary switch itself. Older than that, the temporary switch would be newer than it, and it
     * would come back with the old insulin when its copy is cut.
     */
    private fun permanentTime(temporaryStart: Long, now: Long): Long =
        (now - PERMANENT_BEFORE_COPY).coerceAtLeast(temporaryStart + 1).coerceAtMost(now - 1)

    /** A new record with the profile of [original] (its blocks, name, percentage and time shift), at another time and with [iCfg]. */
    private fun newSwitchLike(original: PS, timestamp: Long, duration: Long, iCfg: ICfg): PS =
        PS(
            timestamp = timestamp,
            basalBlocks = original.basalBlocks,
            isfBlocks = original.isfBlocks,
            icBlocks = original.icBlocks,
            targetBlocks = original.targetBlocks,
            glucoseUnit = original.glucoseUnit,
            profileName = original.profileName,
            timeshift = original.timeshift,
            percentage = original.percentage,
            duration = duration,
            iCfg = iCfg
        )

    override suspend fun createProfileSwitchLike(
        original: PS, timestamp: Long, duration: Long, iCfg: ICfg,
        action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>
    ): PS? {
        val ps = newSwitchLike(original, timestamp, duration, iCfg)
        refusal(ps)?.let {
            aapsLogger.warn(LTag.PROFILE, "Profile switch like ${original.profileName} refused: $it")
            return null
        }
        val result = persistenceLayer.insertOrUpdateProfileSwitch(ps, action, source, note, listValues)
        return result.inserted.firstOrNull() ?: result.updated.firstOrNull()
    }

    override suspend fun profileSwitchLikeRefusal(original: PS, timestamp: Long, duration: Long, iCfg: ICfg): String? =
        refusal(newSwitchLike(original, timestamp, duration, iCfg))

    private companion object {

        /** A temporary switch with less time left is not copied: the pump would only set two profiles a minute apart. */
        const val MIN_REMAINING_TO_COPY = 60_000L

        /** How much older the permanent switch of an insulin change is than the copy of the temporary one. */
        const val PERMANENT_BEFORE_COPY = 1_000L
    }
}