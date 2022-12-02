package info.nightscout.implementation.profile

import info.nightscout.core.extensions.fromConstant
import info.nightscout.core.profile.ProfileSealed
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.database.ValueWrapper
import info.nightscout.database.entities.ProfileSwitch
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.InsertOrUpdateProfileSwitch
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.nsclient.ProcessedDeviceStatusData
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.profile.ProfileStore
import info.nightscout.interfaces.utils.HardLimits
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventEffectiveProfileSwitchChanged
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileFunctionImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val sp: SP,
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val activePlugin: ActivePlugin,
    private val repository: AppRepository,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val hardLimits: HardLimits,
    aapsSchedulers: AapsSchedulers,
    private val fabricPrivacy: FabricPrivacy,
    private val processedDeviceStatusData: ProcessedDeviceStatusData
) : ProfileFunction {

    private var cache = ConcurrentHashMap<Long, Profile?>()

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

    fun getProfileName(time: Long, customized: Boolean, showRemainingTime: Boolean): String {
        var profileName = rh.gs(info.nightscout.core.ui.R.string.no_profile_set)

        val profileSwitch = repository.getEffectiveProfileSwitchActiveAt(time).blockingGet()
        if (profileSwitch is ValueWrapper.Existing) {
            profileName = if (customized) profileSwitch.value.originalCustomizedName else profileSwitch.value.originalProfileName
            if (showRemainingTime && profileSwitch.value.originalDuration != 0L) {
                profileName += dateUtil.untilString(profileSwitch.value.originalEnd, rh)
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
                //aapsLogger.debug(LTag.PROFILE, "Profile cache HIT for $rounded")
                return cache[rounded]
            }
        }
//        aapsLogger.debug("getProfile called for $time")
        //aapsLogger.debug(LTag.PROFILE, "Profile cache MISS for $rounded")
        val ps = repository.getEffectiveProfileSwitchActiveAt(time).blockingGet()
        if (ps is ValueWrapper.Existing) {
            val sealed = ProfileSealed.EPS(ps.value)
            synchronized(cache) {
                cache.put(rounded, sealed)
            }
            return sealed
        }
        // In NSClient mode effective profile may not be received if older than 2 days
        // Try to get it from device status
        // Remove this code after switch to api v3
        if (config.NSCLIENT && ps is ValueWrapper.Absent) {
            processedDeviceStatusData.pumpData?.activeProfileName?.let { activeProfile ->
                activePlugin.activeProfileSource.profile?.getSpecificProfile(activeProfile)?.let { ap ->
                    val sealed = ProfileSealed.Pure(ap)
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

    override fun getRequestedProfile(): ProfileSwitch? = repository.getActiveProfileSwitch(dateUtil.now())

    override fun isProfileChangePending(): Boolean {
        val requested = getRequestedProfile() ?: return false
        val running = getProfile() ?: return true
        return !ProfileSealed.PS(requested).isEqual(running)
    }

    override fun getUnits(): GlucoseUnit =
        if (sp.getString(info.nightscout.core.utils.R.string.key_units, Constants.MGDL) == Constants.MGDL) GlucoseUnit.MGDL
        else GlucoseUnit.MMOL

    override fun buildProfileSwitch(profileStore: ProfileStore, profileName: String, durationInMinutes: Int, percentage: Int, timeShiftInHours: Int, timestamp: Long): ProfileSwitch? {
        val pureProfile = profileStore.getSpecificProfile(profileName) ?: return null
        return ProfileSwitch(
            timestamp = timestamp,
            basalBlocks = pureProfile.basalBlocks,
            isfBlocks = pureProfile.isfBlocks,
            icBlocks = pureProfile.icBlocks,
            targetBlocks = pureProfile.targetBlocks,
            glucoseUnit = ProfileSwitch.GlucoseUnit.fromConstant(pureProfile.glucoseUnit),
            profileName = profileName,
            timeshift = T.hours(timeShiftInHours.toLong()).msecs(),
            percentage = percentage,
            duration = T.mins(durationInMinutes.toLong()).msecs(),
            insulinConfiguration = activePlugin.activeInsulin.insulinConfiguration.also {
                it.insulinEndTime = (pureProfile.dia * 3600 * 1000).toLong()
            }
        )
    }

    override fun createProfileSwitch(profileStore: ProfileStore, profileName: String, durationInMinutes: Int, percentage: Int, timeShiftInHours: Int, timestamp: Long): Boolean {
        val ps = buildProfileSwitch(profileStore, profileName, durationInMinutes, percentage, timeShiftInHours, timestamp) ?: return false
        disposable += repository.runTransactionForResult(InsertOrUpdateProfileSwitch(ps))
            .subscribe({ result ->
                           result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted ProfileSwitch $it") }
                           result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated ProfileSwitch $it") }
                       }, {
                           aapsLogger.error(LTag.DATABASE, "Error while saving ProfileSwitch", it)
                       })
        return true
    }

    override fun createProfileSwitch(durationInMinutes: Int, percentage: Int, timeShiftInHours: Int): Boolean {
        val profile = repository.getPermanentProfileSwitch(dateUtil.now()) ?: return false
        val profileStore = activePlugin.activeProfileSource.profile ?: return false
        val ps = buildProfileSwitch(profileStore, profile.profileName, durationInMinutes, percentage, 0, dateUtil.now()) ?: return false
        val validity = ProfileSealed.PS(ps).isValid(
            rh.gs(info.nightscout.core.ui.R.string.careportal_profileswitch),
            activePlugin.activePump,
            config,
            rh,
            rxBus,
            hardLimits,
            false
        )
        var returnValue = true
        if (validity.isValid) {
            repository.runTransactionForResult(InsertOrUpdateProfileSwitch(ps))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving ProfileSwitch", it)
                    returnValue = false
                }
                .blockingGet()
                .also { result ->
                    result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted ProfileSwitch $it") }
                    result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated ProfileSwitch $it") }
                }
        } else returnValue = false
        return returnValue
    }
}