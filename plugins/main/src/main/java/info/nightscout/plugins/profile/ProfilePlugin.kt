package info.nightscout.plugins.profile

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.core.extensions.blockFromJsonArray
import info.nightscout.core.extensions.pureProfileFromJson
import info.nightscout.core.profile.ProfileSealed
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.ui.toast.ToastUtils
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.XDripBroadcast
import info.nightscout.interfaces.notifications.Notification
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.profile.ProfileInstantiator
import info.nightscout.interfaces.profile.ProfileSource
import info.nightscout.interfaces.profile.ProfileStore
import info.nightscout.interfaces.profile.PureProfile
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.interfaces.utils.HardLimits
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.plugins.R
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventLocalProfileChanged
import info.nightscout.rx.events.EventProfileStoreChanged
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class ProfilePlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    rh: ResourceHelper,
    private val sp: SP,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    private val hardLimits: HardLimits,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val profileInstantiator: ProfileInstantiator
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.PROFILE)
        .fragmentClass(ProfileFragment::class.java.name)
        .enableByDefault(true)
        .pluginIcon(R.drawable.ic_local_profile)
        .pluginName(R.string.localprofile)
        .shortName(R.string.localprofile_shortname)
        .description(R.string.description_profile_local)
        .setDefault(),
    aapsLogger, rh, injector
), ProfileSource {

    private var rawProfile: ProfileStore? = null

    private val defaultArray = "[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":0}]"

    override fun onStart() {
        super.onStart()
        loadSettings()
    }

    var isEdited: Boolean = false
    var profiles: ArrayList<ProfileSource.SingleProfile> = ArrayList()

    val numOfProfiles get() = profiles.size
    override var currentProfileIndex = 0

    override fun currentProfile(): ProfileSource.SingleProfile? = if (numOfProfiles > 0 && currentProfileIndex < numOfProfiles) profiles[currentProfileIndex] else null

    @Synchronized
    fun isValidEditState(activity: FragmentActivity?): Boolean {
        val pumpDescription = activePlugin.activePump.pumpDescription
        with(profiles[currentProfileIndex]) {
            if (dia < hardLimits.minDia() || dia > hardLimits.maxDia()) {
                ToastUtils.errorToast(activity, rh.gs(R.string.value_out_of_hard_limits, rh.gs(R.string.profile_dia), dia))
                return false
            }
            if (name.isNullOrEmpty()) {
                ToastUtils.errorToast(activity, rh.gs(R.string.missing_profile_name))
                return false
            }
            if (blockFromJsonArray(ic, dateUtil)?.all { it.amount < hardLimits.minIC() || it.amount > hardLimits.maxIC() } != false) {
                ToastUtils.errorToast(activity, rh.gs(R.string.error_in_ic_values))
                return false
            }
            val low = blockFromJsonArray(targetLow, dateUtil)
            val high = blockFromJsonArray(targetHigh, dateUtil)
            if (mgdl) {
                if (blockFromJsonArray(isf, dateUtil)?.all { hardLimits.isInRange(it.amount, HardLimits.MIN_ISF, HardLimits.MAX_ISF) } == false) {
                    ToastUtils.errorToast(activity, rh.gs(R.string.error_in_isf_values))
                    return false
                }
                if (blockFromJsonArray(basal, dateUtil)?.all { it.amount < pumpDescription.basalMinimumRate || it.amount > 10.0 } != false) {
                    ToastUtils.errorToast(activity, rh.gs(R.string.error_in_basal_values))
                    return false
                }
                if (low?.all { hardLimits.isInRange(it.amount, HardLimits.VERY_HARD_LIMIT_MIN_BG[0], HardLimits.VERY_HARD_LIMIT_MIN_BG[1]) } == false) {
                    ToastUtils.errorToast(activity, rh.gs(R.string.error_in_target_values))
                    return false
                }
                if (high?.all { hardLimits.isInRange(it.amount, HardLimits.VERY_HARD_LIMIT_MAX_BG[0], HardLimits.VERY_HARD_LIMIT_MAX_BG[1]) } == false) {
                    ToastUtils.errorToast(activity, rh.gs(R.string.error_in_target_values))
                    return false
                }
            } else {
                if (blockFromJsonArray(isf, dateUtil)?.all { hardLimits.isInRange(Profile.toMgdl(it.amount, GlucoseUnit.MMOL), HardLimits.MIN_ISF, HardLimits.MAX_ISF) } == false) {
                    ToastUtils.errorToast(activity, rh.gs(R.string.error_in_isf_values))
                    return false
                }
                if (blockFromJsonArray(basal, dateUtil)?.all { it.amount < pumpDescription.basalMinimumRate || it.amount > 10.0 } != false) {
                    ToastUtils.errorToast(activity, rh.gs(R.string.error_in_basal_values))
                    return false
                }
                if (low?.all { hardLimits.isInRange(Profile.toMgdl(it.amount, GlucoseUnit.MMOL), HardLimits.VERY_HARD_LIMIT_MIN_BG[0], HardLimits.VERY_HARD_LIMIT_MIN_BG[1]) } == false) {
                    ToastUtils.errorToast(activity, rh.gs(R.string.error_in_target_values))
                    return false
                }
                if (high?.all { hardLimits.isInRange(Profile.toMgdl(it.amount, GlucoseUnit.MMOL), HardLimits.VERY_HARD_LIMIT_MAX_BG[0], HardLimits.VERY_HARD_LIMIT_MAX_BG[1]) } == false) {
                    ToastUtils.errorToast(activity, rh.gs(R.string.error_in_target_values))
                    return false
                }
            }
            low?.let {
                high?.let {
                    for (i in low.indices) if (low[i].amount > high[i].amount) {
                        ToastUtils.errorToast(activity, rh.gs(R.string.error_in_target_values))
                        return false
                    }
                }
            }
        }
        return true
    }

    @Synchronized
    fun getEditedProfile(): PureProfile? {
        val profile = JSONObject()
        with(profiles[currentProfileIndex]) {
            profile.put("dia", dia)
            profile.put("carbratio", ic)
            profile.put("sens", isf)
            profile.put("basal", basal)
            profile.put("target_low", targetLow)
            profile.put("target_high", targetHigh)
            profile.put("units", if (mgdl) Constants.MGDL else Constants.MMOL)
            profile.put("timezone", TimeZone.getDefault().id)
        }
        val defaultUnits = JsonHelper.safeGetStringAllowNull(profile, "units", null)
        return pureProfileFromJson(profile, dateUtil, defaultUnits)
    }

    @Synchronized
    override fun storeSettings(activity: FragmentActivity?) {
        for (i in 0 until numOfProfiles) {
            profiles[i].run {
                name?.let { name ->
                    val localProfileNumbered = Constants.LOCAL_PROFILE + "_" + i + "_"
                    sp.putString(localProfileNumbered + "name", name)
                    sp.putBoolean(localProfileNumbered + "mgdl", mgdl)
                    sp.putDouble(localProfileNumbered + "dia", dia)
                    sp.putString(localProfileNumbered + "ic", ic.toString())
                    sp.putString(localProfileNumbered + "isf", isf.toString())
                    sp.putString(localProfileNumbered + "basal", basal.toString())
                    sp.putString(localProfileNumbered + "targetlow", targetLow.toString())
                    sp.putString(localProfileNumbered + "targethigh", targetHigh.toString())
                }
            }
        }
        sp.putInt(Constants.LOCAL_PROFILE + "_profiles", numOfProfiles)

        sp.putLong(R.string.key_local_profile_last_change, dateUtil.now())
        createAndStoreConvertedProfile()
        isEdited = false
        aapsLogger.debug(LTag.PROFILE, "Storing settings: " + rawProfile?.data.toString())
        rxBus.send(EventProfileStoreChanged())
        var namesOK = true
        profiles.forEach {
            val name = it.name ?: "."
            if (name.contains(".")) namesOK = false
        }
        if (!namesOK) activity?.let {
            OKDialog.show(it, "", rh.gs(R.string.profile_name_contains_dot))
        }
    }

    @Synchronized
    fun loadSettings() {
        val numOfProfiles = sp.getInt(Constants.LOCAL_PROFILE + "_profiles", 0)
        profiles.clear()
//        numOfProfiles = max(numOfProfiles, 1) // create at least one default profile if none exists

        for (i in 0 until numOfProfiles) {
            val p = ProfileSource.SingleProfile()
            val localProfileNumbered = Constants.LOCAL_PROFILE + "_" + i + "_"

            p.name = sp.getString(localProfileNumbered + "name", Constants.LOCAL_PROFILE + i)
            if (isExistingName(p.name)) continue
            p.mgdl = sp.getBoolean(localProfileNumbered + "mgdl", false)
            p.dia = sp.getDouble(localProfileNumbered + "dia", Constants.defaultDIA)
            try {
                p.ic = JSONArray(sp.getString(localProfileNumbered + "ic", defaultArray))
                p.isf = JSONArray(sp.getString(localProfileNumbered + "isf", defaultArray))
                p.basal = JSONArray(sp.getString(localProfileNumbered + "basal", defaultArray))
                p.targetLow = JSONArray(sp.getString(localProfileNumbered + "targetlow", defaultArray))
                p.targetHigh = JSONArray(sp.getString(localProfileNumbered + "targethigh", defaultArray))
                profiles.add(p)
            } catch (e: JSONException) {
                aapsLogger.error("Exception", e)
            }
        }
        isEdited = false
        createAndStoreConvertedProfile()
    }

    @Synchronized
    fun loadFromStore(store: ProfileStore) {
        try {
            val newProfiles: ArrayList<ProfileSource.SingleProfile> = ArrayList()
            for (p in store.getProfileList()) {
                val profile = store.getSpecificProfile(p.toString())
                val validityCheck = profile?.let { ProfileSealed.Pure(profile).isValid("NS", activePlugin.activePump, config, rh, rxBus, hardLimits, false) } ?: Profile.ValidityCheck()
                if (profile != null && validityCheck.isValid) {
                    val sp = copyFrom(profile, p.toString())
                    sp.name = p.toString()
                    newProfiles.add(sp)
                } else {
                    activePlugin.activeOverview.addNotificationWithDialogResponse(
                        Notification.INVALID_PROFILE_NOT_ACCEPTED,
                        rh.gs(R.string.invalid_profile_not_accepted, p.toString()),
                        Notification.NORMAL,
                        R.string.view,
                        rh.gs(R.string.errors),
                        validityCheck.reasons.joinToString(separator = "\n")
                    )
                }
            }
            if (newProfiles.size > 0) {
                profiles = newProfiles
                currentProfileIndex = 0
                isEdited = false
                createAndStoreConvertedProfile()
                aapsLogger.debug(LTag.PROFILE, "Accepted ${profiles.size} profiles")
                rxBus.send(EventLocalProfileChanged())
            } else
                aapsLogger.debug(LTag.PROFILE, "ProfileStore not accepted")
        } catch (e: Exception) {
            aapsLogger.error("Error loading ProfileStore", e)
        }
    }

    override fun copyFrom(pureProfile: PureProfile, newName: String): ProfileSource.SingleProfile {
        var verifiedName = newName
        if (rawProfile?.getSpecificProfile(newName) != null) {
            verifiedName += " " + dateUtil.now().toString()
        }
        val profile = ProfileSealed.Pure(pureProfile)
        val pureJson = pureProfile.jsonObject
        val sp = ProfileSource.SingleProfile()
        sp.name = verifiedName
        sp.mgdl = profile.units == GlucoseUnit.MGDL
        sp.dia = pureJson.getDouble("dia")
        sp.ic = pureJson.getJSONArray("carbratio")
        sp.isf = pureJson.getJSONArray("sens")
        sp.basal = pureJson.getJSONArray("basal")
        sp.targetLow = pureJson.getJSONArray("target_low")
        sp.targetHigh = pureJson.getJSONArray("target_high")
        return sp
    }

    private fun isExistingName(name: String?): Boolean {
        for (p in profiles) {
            if (p.name == name) return true
        }
        return false
    }

    /*
        {
            "_id": "576264a12771b7500d7ad184",
            "startDate": "2016-06-16T08:35:00.000Z",
            "defaultProfile": "Default",
            "store": {
                "Default": {
                    "dia": "3",
                    "carbratio": [{
                        "time": "00:00",
                        "value": "30"
                    }],
                    "carbs_hr": "20",
                    "delay": "20",
                    "sens": [{
                        "time": "00:00",
                        "value": "100"
                    }],
                    "timezone": "UTC",
                    "basal": [{
                        "time": "00:00",
                        "value": "0.1"
                    }],
                    "target_low": [{
                        "time": "00:00",
                        "value": "0"
                    }],
                    "target_high": [{
                        "time": "00:00",
                        "value": "0"
                    }],
                    "startDate": "1970-01-01T00:00:00.000Z",
                    "units": "mmol"
                }
            },
            "created_at": "2016-06-16T08:34:41.256Z"
        }
        */
    private fun createAndStoreConvertedProfile() {
        rawProfile = createProfileStore()
    }

    fun addNewProfile() {
        var free = 0
        for (i in 1..10000) {
            if (rawProfile?.getSpecificProfile(Constants.LOCAL_PROFILE + i) == null) {
                free = i
                break
            }
        }
        val p = ProfileSource.SingleProfile()
        p.name = Constants.LOCAL_PROFILE + free
        p.mgdl = profileFunction.getUnits() == GlucoseUnit.MGDL
        p.dia = Constants.defaultDIA
        p.ic = JSONArray(defaultArray)
        p.isf = JSONArray(defaultArray)
        p.basal = JSONArray(defaultArray)
        p.targetLow = JSONArray(defaultArray)
        p.targetHigh = JSONArray(defaultArray)
        profiles.add(p)
        currentProfileIndex = profiles.size - 1
        createAndStoreConvertedProfile()
        storeSettings()
    }

    fun cloneProfile() {
        val p = profiles[currentProfileIndex].deepClone()
        p.name = p.name + " copy"
        profiles.add(p)
        currentProfileIndex = profiles.size - 1
        createAndStoreConvertedProfile()
        storeSettings()
        isEdited = false
    }

    override fun addProfile(p: ProfileSource.SingleProfile) {
        profiles.add(p)
        currentProfileIndex = profiles.size - 1
        createAndStoreConvertedProfile()
        storeSettings()
        isEdited = false
    }

    fun removeCurrentProfile() {
        profiles.removeAt(currentProfileIndex)
        if (profiles.size == 0) addNewProfile()
        currentProfileIndex = 0
        createAndStoreConvertedProfile()
        storeSettings()
        isEdited = false
    }

    fun createProfileStore(): ProfileStore {
        val json = JSONObject()
        val store = JSONObject()

        try {
            for (i in 0 until numOfProfiles) {
                profiles[i].run {
                    name?.let { name ->
                        val profile = JSONObject()
                        profile.put("dia", dia)
                        profile.put("carbratio", ic)
                        profile.put("sens", isf)
                        profile.put("basal", basal)
                        profile.put("target_low", targetLow)
                        profile.put("target_high", targetHigh)
                        profile.put("units", if (mgdl) Constants.MGDL else Constants.MMOL)
                        profile.put("timezone", TimeZone.getDefault().id)
                        store.put(name, profile)
                    }
                }
            }
            if (numOfProfiles > 0) json.put("defaultProfile", currentProfile()?.name)
            val startDate = sp.getLong(R.string.key_local_profile_last_change, dateUtil.now())
            json.put("startDate", dateUtil.toISOAsUTC(startDate))
            json.put("store", store)
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }

        return profileInstantiator.storeInstance(json)
    }

    override val profile: ProfileStore?
        get() = rawProfile

    override val profileName: String
        get() = rawProfile?.getDefaultProfile()?.let {
            DecimalFormatter.to2Decimal(ProfileSealed.Pure(it).percentageBasalSum()) + "U "
        } ?: "INVALID"

    // cannot be inner class because of needed injection
    class NSProfileWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {

        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var aapsLogger: AAPSLogger
        @Inject lateinit var rxBus: RxBus
        @Inject lateinit var dateUtil: DateUtil
        @Inject lateinit var dataWorkerStorage: DataWorkerStorage
        @Inject lateinit var sp: SP
        @Inject lateinit var config: Config
        @Inject lateinit var profilePlugin: ProfilePlugin
        @Inject lateinit var xDripBroadcast: XDripBroadcast
        @Inject lateinit var profileInstantiator: ProfileInstantiator

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }

        override fun doWork(): Result {
            val profileJson = dataWorkerStorage.pickupJSONObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1))
                ?: return Result.failure(workDataOf("Error" to "missing input data"))
            xDripBroadcast.sendProfile(profileJson)
            if (sp.getBoolean(R.string.key_ns_receive_profile_store, true) || config.NSCLIENT) {
                val store = profileInstantiator.storeInstance(profileJson)
                val createdAt = store.getStartDate()
                val lastLocalChange = sp.getLong(R.string.key_local_profile_last_change, 0)
                aapsLogger.debug(LTag.PROFILE, "Received profileStore: createdAt: $createdAt Local last modification: $lastLocalChange")
                @Suppress("LiftReturnOrAssignment")
                if (createdAt > lastLocalChange || createdAt % 1000 == 0L) {// whole second means edited in NS
                    profilePlugin.loadFromStore(store)
                    aapsLogger.debug(LTag.PROFILE, "Received profileStore: $profileJson")
                    return Result.success(workDataOf("Data" to profileJson.toString().substring(0..Integer.min(5000, profileJson.length()))))
                } else
                    return Result.success(workDataOf("Result" to "Unchanged. Ignoring"))
            }
            return Result.success(workDataOf("Result" to "Sync not enabled"))
        }
    }

}