package info.nightscout.androidaps.plugins.configBuilder

import androidx.collection.LongSparseArray
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.data.ProfileSealed
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.entities.ProfileSwitch
import info.nightscout.androidaps.database.transactions.InsertOrUpdateProfileSwitch
import info.nightscout.androidaps.events.EventEffectiveProfileSwitchChanged
import info.nightscout.androidaps.extensions.fromConstant
import info.nightscout.androidaps.interfaces.*
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.sync.nsclient.data.DeviceStatusData
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.HardLimits
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileFunctionImplementation @Inject constructor(
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
    private val deviceStatusData: DeviceStatusData
) : ProfileFunction {

    val cache = LongSparseArray<Profile>()

    private val disposable = CompositeDisposable()

    init {
        disposable += rxBus
            .toObservable(EventEffectiveProfileSwitchChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe(
                {
                    synchronized(cache) {
                        for (index in cache.size() - 1 downTo 0) {
                            if (cache.keyAt(index) > it.startDate) {
                                aapsLogger.debug(LTag.AUTOSENS, "Removing from profileCache: " + dateUtil.dateAndTimeAndSecondsString(cache.keyAt(index)))
                                cache.removeAt(index)
                            } else {
                                break
                            }
                        }
                    }
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
        var profileName = rh.gs(R.string.noprofileset)

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
            if (cache.size() > 30000) {
                cache.clear()
                aapsLogger.debug("Profile cache cleared")
            }
            val cached = cache[rounded]
            if (cached != null) return cached
        }
//        aapsLogger.debug("getProfile called for $time")
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
            deviceStatusData.pumpData?.activeProfileName?.let { activeProfile ->
                activePlugin.activeProfileSource.profile?.getSpecificProfile(activeProfile)?.let { ap ->
                    val sealed = ProfileSealed.Pure(ap)
                    synchronized(cache) {
                        cache.put(rounded, sealed)
                    }
                    return sealed
                }

            }
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
        if (sp.getString(R.string.key_units, Constants.MGDL) == Constants.MGDL) GlucoseUnit.MGDL
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
        val ps = buildProfileSwitch(profileStore, profileName, durationInMinutes, percentage, timeShiftInHours, timestamp)  ?: return false
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
            rh.gs(info.nightscout.androidaps.automation.R.string.careportal_profileswitch),
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
