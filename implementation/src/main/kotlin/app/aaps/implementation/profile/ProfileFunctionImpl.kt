package app.aaps.implementation.profile

import androidx.annotation.VisibleForTesting
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.PS
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventEffectiveProfileSwitchChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.profile.ProfileSealed
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileFunctionImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val activePlugin: ActivePlugin,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val hardLimits: HardLimits,
    aapsSchedulers: AapsSchedulers,
    private val fabricPrivacy: FabricPrivacy,
    private val processedDeviceStatusData: ProcessedDeviceStatusData
) : ProfileFunction {

    @VisibleForTesting
    val cache = ConcurrentHashMap<Long, Profile?>()

    private val disposable = CompositeDisposable()

    init {
        disposable += rxBus
            .toObservable(EventEffectiveProfileSwitchChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe(
                {
                    synchronized(cache) { cache.keys.removeIf { key -> key > it.startDate } }
                }, fabricPrivacy::logException
            )
    }

    override fun getProfileName(): String =
        getProfileName(System.currentTimeMillis(), customized = true, showRemainingTime = false)

    override fun getOriginalProfileName(): String =
        getProfileName(System.currentTimeMillis(), customized = false, showRemainingTime = false)

    override fun getProfileNameWithRemainingTime(): String =
        getProfileName(System.currentTimeMillis(), customized = true, showRemainingTime = true)

    private fun getProfileName(time: Long, customized: Boolean, showRemainingTime: Boolean): String {
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

    override fun isProfileValid(from: String): Boolean = getProfile() != null

    override fun getProfile(): Profile? =
        getProfile(dateUtil.now())

    override fun getProfile(time: Long): Profile? {
        val rounded = time - time % 1000
        // Clear cache after longer use
        synchronized(cache) {
            if (cache.keys.size > 30000) {
                cache.clear()
                aapsLogger.debug("Profile cache cleared")
            }
            if (cache.containsKey(rounded)) {
                return cache[rounded]
            }
        }
        val ps = persistenceLayer.getEffectiveProfileSwitchActiveAt(time)
        if (ps != null) {
            val sealed = ProfileSealed.EPS(ps, activePlugin)
            synchronized(cache) {
                cache.put(rounded, sealed)
            }
            return sealed
        }
        // In NSClient mode effective profile may not be received if older than 2 days
        // Try to get it from device status
        // Remove this code after switch to api v3
        // ps == null
        if (config.AAPSCLIENT) {
            processedDeviceStatusData.pumpData?.activeProfileName?.let { activeProfile ->
                activePlugin.activeProfileSource.profile?.getSpecificProfile(activeProfile)?.let { ap ->
                    val sealed = ProfileSealed.Pure(ap, activePlugin)
                    synchronized(cache) {
                        cache.put(rounded, sealed)
                    }
                    return sealed
                }

            }
        }

        synchronized(cache) {
            cache.remove(rounded)
        }
        return null
    }

    override fun getRequestedProfile(): PS? = persistenceLayer.getProfileSwitchActiveAt(dateUtil.now())

    override fun isProfileChangePending(): Boolean {
        val requested = getRequestedProfile() ?: return false
        val running = getProfile() ?: return true
        return !ProfileSealed.PS(requested, activePlugin).isEqual(running)
    }

    override fun getUnits(): GlucoseUnit =
        if (preferences.get(StringKey.GeneralUnits) == GlucoseUnit.MGDL.asText) GlucoseUnit.MGDL
        else GlucoseUnit.MMOL

    override fun buildProfileSwitch(profileStore: ProfileStore, profileName: String, durationInMinutes: Int, percentage: Int, timeShiftInHours: Int, timestamp: Long): PS? {
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
            iCfg = activePlugin.activeInsulin.iCfg.also {
                it.insulinEndTime = (pureProfile.dia * 3600 * 1000).toLong()
            }
        )
    }

    override fun createProfileSwitch(
        profileStore: ProfileStore, profileName: String, durationInMinutes: Int, percentage: Int, timeShiftInHours: Int, timestamp: Long,
        action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>
    ): Boolean {
        val ps = buildProfileSwitch(profileStore, profileName, durationInMinutes, percentage, timeShiftInHours, timestamp) ?: return false
        disposable += persistenceLayer.insertOrUpdateProfileSwitch(ps, action, source, note, listValues).subscribe()
        return true
    }

    override fun createProfileSwitch(
        durationInMinutes: Int, percentage: Int, timeShiftInHours: Int,
        action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>
    ): Boolean {
        val profile = persistenceLayer.getPermanentProfileSwitchActiveAt(dateUtil.now()) ?: return false
        val profileStore = activePlugin.activeProfileSource.profile ?: return false
        val ps = buildProfileSwitch(profileStore, profile.profileName, durationInMinutes, percentage, timeShiftInHours, dateUtil.now()) ?: return false
        val validity = ProfileSealed.PS(ps, activePlugin).isValid(
            rh.gs(app.aaps.core.ui.R.string.careportal_profileswitch),
            activePlugin.activePump,
            config,
            rh,
            rxBus,
            hardLimits,
            false
        )
        if (validity.isValid) {
            disposable += persistenceLayer.insertOrUpdateProfileSwitch(ps, action, source, note, listValues).subscribe()
            return true
        }
        return false
    }
}