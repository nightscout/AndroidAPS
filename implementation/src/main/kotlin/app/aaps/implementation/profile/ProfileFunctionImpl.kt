package app.aaps.implementation.profile

import androidx.annotation.VisibleForTesting
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.PS
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.profile.ProfileSealed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileFunctionImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val rh: ResourceHelper,
    private val activePlugin: ActivePlugin,
    private val profileRepository: ProfileRepository,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val hardLimits: HardLimits,
    private val notificationManager: NotificationManager,
    @ApplicationScope private val appScope: CoroutineScope
) : ProfileFunction {

    @VisibleForTesting
    val cache = ConcurrentHashMap<Long, EffectiveProfile>()

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
    @VisibleForTesting
    val canonicalEps = HashMap<Long, EPS>()

    private val _runningICfg = MutableStateFlow<ICfg?>(null)
    override val runningICfg: StateFlow<ICfg?> = _runningICfg.asStateFlow()

    init {
        // Populate the mirror off the main thread so the synchronous readers never block on the DB.
        appScope.launch { _runningICfg.value = getProfile()?.iCfg?.takeIf { it.isUsable } }
        persistenceLayer.observeChanges(EPS::class.java)
            .collectResilient(appScope, aapsLogger, LTag.PROFILE) { epsList ->
                epsList.minOfOrNull { it.timestamp }?.let { timestamp ->
                    // Cache keys are rounded down to the second (see getProfile), so compare against
                    // the rounded timestamp with >= to also invalidate the entry for the second in
                    // which the change happened. A strict > against the raw ms timestamp would leave
                    // the current-second entry stale and getProfile() would keep returning the old EPS.
                    val rounded = timestamp - timestamp % 1000
                    synchronized(cache) {
                        cache.keys.removeIf { key -> key >= rounded }
                        // An edited EPS keeps its id but changes content, so a stale canonical copy would be
                        // re-served on the next miss. Clear the whole map (not just changed ids): it rebuilds
                        // lazily and any surviving older wrapper already holds its own reference — nothing dangles.
                        canonicalEps.clear()
                    }
                }
                // Refresh the mirror in the SAME subscription, after the invalidation above — a separate
                // collector on this flow has no ordering guarantee against it and could re-read the stale
                // cache, pinning the mirror to the previous insulin until the next profile change.
                _runningICfg.value = getProfile()?.iCfg?.takeIf { it.isUsable }
            }
    }

    override suspend fun getProfileName(): String =
        getProfileName(System.currentTimeMillis(), customized = true, showRemainingTime = false)

    override suspend fun getOriginalProfileName(): String =
        getProfileName(System.currentTimeMillis(), customized = false, showRemainingTime = false)

    override suspend fun getProfileNameWithRemainingTime(): String =
        getProfileName(System.currentTimeMillis(), customized = true, showRemainingTime = true)

    private suspend fun getProfileName(time: Long, customized: Boolean, showRemainingTime: Boolean): String {
        var profileName = rh.gs(app.aaps.core.ui.R.string.no_profile_set)

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
        synchronized(cache) {
            cache.clear()
            canonicalEps.clear()
        }
        _runningICfg.value = getProfile()?.iCfg?.takeIf { it.isUsable }
    }

    override suspend fun getProfile(): EffectiveProfile? =
        getProfile(dateUtil.now())

    override suspend fun getProfile(time: Long): EffectiveProfile? {
        val rounded = time - time % 1000
        // Clear cache after longer use
        synchronized(cache) {
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
            synchronized(cache) {
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
                    synchronized(cache) {
                        cache.put(rounded, sealed)
                    }
                    return sealed
                }

            }
        }
        */
        synchronized(cache) {
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
        val validity = ProfileSealed.PS(ps, activePlugin).isValid(
            rh.gs(app.aaps.core.ui.R.string.careportal_profileswitch),
            activePlugin.activePump,
            config,
            rh,
            notificationManager,
            hardLimits,
            false
        )
        if (!validity.isValid) return null
        val result = persistenceLayer.insertOrUpdateProfileSwitch(ps, action, source, note, listValues)
        return result.inserted.firstOrNull() ?: result.updated.firstOrNull()
    }

    override suspend fun createProfileSwitch(
        durationInMinutes: Int, percentage: Int, timeShiftInHours: Int,
        action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>
    ): PS? {
        val profile = persistenceLayer.getPermanentProfileSwitchActiveAt(dateUtil.now()) ?: return null
        val profileStore = profileRepository.profile.value ?: return null
        val ps = buildProfileSwitch(profileStore, profile.profileName, durationInMinutes, percentage, timeShiftInHours, dateUtil.now(), profile.iCfg) ?: return null
        val validity = ProfileSealed.PS(ps, activePlugin).isValid(
            rh.gs(app.aaps.core.ui.R.string.careportal_profileswitch),
            activePlugin.activePump,
            config,
            rh,
            notificationManager,
            hardLimits,
            false
        )
        if (validity.isValid) {
            val result = persistenceLayer.insertOrUpdateProfileSwitch(ps, action, source, note, listValues)
            return result.inserted.firstOrNull() ?: result.updated.firstOrNull()
        }
        return null
    }

    override suspend fun createProfileSwitchWithNewInsulin(iCfg: ICfg, source: Sources): Boolean {
        val profile = getProfile()
        val eps = (profile as? ProfileSealed.EPS)?.value ?: return false
        val profileStore = profileRepository.profile.value ?: return false
        val profileName = eps.originalProfileName
        val percentage = eps.originalPercentage
        val timeshiftHours = T.msecs(eps.originalTimeshift).hours().toInt()
        val now = dateUtil.now()

        val durationMinutes = if (eps.originalDuration > 0) {
            ((eps.originalDuration - (now - eps.timestamp)) / 60_000L).coerceAtLeast(0).toInt()
        } else 0

        return createProfileSwitch(
            profileStore = profileStore,
            profileName = profileName,
            durationInMinutes = durationMinutes,
            percentage = percentage,
            timeShiftInHours = timeshiftHours,
            timestamp = now,
            action = Action.PROFILE_SWITCH,
            source = source,
            listValues = listOfNotNull(
                ValueWithUnit.SimpleString(profileName),
                ValueWithUnit.SimpleString(iCfg.insulinLabel),
                ValueWithUnit.Percent(percentage),
                ValueWithUnit.Hour(timeshiftHours).takeIf { timeshiftHours != 0 },
                ValueWithUnit.Minute(durationMinutes).takeIf { durationMinutes != 0 }
            ),
            iCfg = iCfg
        ) != null
    }
}