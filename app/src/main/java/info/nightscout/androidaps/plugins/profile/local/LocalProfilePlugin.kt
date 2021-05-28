package info.nightscout.androidaps.plugins.profile.local

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.ProfileSealed
import info.nightscout.androidaps.data.PureProfile
import info.nightscout.androidaps.events.EventProfileStoreChanged
import info.nightscout.androidaps.extensions.blockFromJsonArray
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.profile.local.events.EventLocalProfileChanged
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.HardLimits
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.Integer.min
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.ArrayList

@Singleton
class LocalProfilePlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    private val rxBus: RxBusWrapper,
    resourceHelper: ResourceHelper,
    private val sp: SP,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    private val hardLimits: HardLimits,
    private val dateUtil: DateUtil
) : PluginBase(PluginDescription()
    .mainType(PluginType.PROFILE)
    .fragmentClass(LocalProfileFragment::class.java.name)
    .enableByDefault(true)
    .pluginIcon(R.drawable.ic_local_profile)
    .pluginName(R.string.localprofile)
    .shortName(R.string.localprofile_shortname)
    .description(R.string.description_profile_local)
    .setDefault(),
    aapsLogger, resourceHelper, injector
), ProfileSource {

    private var rawProfile: ProfileStore? = null

    private val defaultArray = "[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":0}]"

    override fun onStart() {
        super.onStart()
        loadSettings()
    }

    class SingleProfile {

        internal var name: String? = null
        internal var mgdl: Boolean = false
        internal var dia: Double = Constants.defaultDIA
        internal var ic: JSONArray? = null
        internal var isf: JSONArray? = null
        internal var basal: JSONArray? = null
        internal var targetLow: JSONArray? = null
        internal var targetHigh: JSONArray? = null

        fun deepClone(): SingleProfile {
            val sp = SingleProfile()
            sp.name = name
            sp.mgdl = mgdl
            sp.dia = dia
            sp.ic = JSONArray(ic.toString())
            sp.isf = JSONArray(isf.toString())
            sp.basal = JSONArray(basal.toString())
            sp.targetLow = JSONArray(targetLow.toString())
            sp.targetHigh = JSONArray(targetHigh.toString())
            return sp
        }

    }

    var isEdited: Boolean = false
    var profiles: ArrayList<SingleProfile> = ArrayList()

    var numOfProfiles = 0
    internal var currentProfileIndex = 0

    fun currentProfile(): SingleProfile? = if (numOfProfiles > 0) profiles[currentProfileIndex] else null

    @Synchronized
    fun isValidEditState(): Boolean {
        val pumpDescription = activePlugin.activePump.pumpDescription
        with(profiles[currentProfileIndex]) {
            if (dia < hardLimits.minDia() || dia > hardLimits.maxDia()) return false
            if (name.isNullOrEmpty()) return false
            if (blockFromJsonArray(ic, dateUtil)?.any { it.amount < hardLimits.minIC() || it.amount > hardLimits.maxIC() } != false) return false
            if (blockFromJsonArray(isf, dateUtil)?.any { it.amount < HardLimits.MIN_ISF || it.amount > HardLimits.MAX_ISF } != false) return false
            if (blockFromJsonArray(basal, dateUtil)?.any { it.amount < pumpDescription.basalMinimumRate || it.amount > 10.0 } != false) return false
            val low = blockFromJsonArray(targetLow, dateUtil)
            val high = blockFromJsonArray(targetHigh, dateUtil)
            if (profileFunction.getUnits() == GlucoseUnit.MGDL) {
                if (low?.any { it.amount < HardLimits.VERY_HARD_LIMIT_TARGET_BG[0].toDouble() || it.amount > HardLimits.VERY_HARD_LIMIT_TARGET_BG[1].toDouble() } != false) return false
                if (high?.any { it.amount < HardLimits.VERY_HARD_LIMIT_TARGET_BG[0].toDouble() || it.amount > HardLimits.VERY_HARD_LIMIT_TARGET_BG[1].toDouble() } != false) return false
            } else {
                if (low?.any { it.amount < Profile.fromMgdlToUnits(HardLimits.VERY_HARD_LIMIT_TARGET_BG[0].toDouble(), GlucoseUnit.MMOL) || it.amount > Profile.fromMgdlToUnits(HardLimits.VERY_HARD_LIMIT_TARGET_BG[1].toDouble(), GlucoseUnit.MMOL) } != false) return false
                if (high?.any { it.amount < Profile.fromMgdlToUnits(HardLimits.VERY_HARD_LIMIT_TARGET_BG[0].toDouble(), GlucoseUnit.MMOL) || it.amount > Profile.fromMgdlToUnits(HardLimits.VERY_HARD_LIMIT_TARGET_BG[1].toDouble(), GlucoseUnit.MMOL) } != false) return false
            }
            for (i in low.indices) if (low[i].amount > high[i].amount) return false
        }
        return true
    }

    @Synchronized
    fun storeSettings(activity: FragmentActivity? = null) {
        for (i in 0 until numOfProfiles) {
            profiles[i].run {
                val localProfileNumbered = Constants.LOCAL_PROFILE + "_" + i + "_"
                sp.putString(localProfileNumbered + "name", name!!)
                sp.putBoolean(localProfileNumbered + "mgdl", mgdl)
                sp.putDouble(localProfileNumbered + "dia", dia)
                sp.putString(localProfileNumbered + "ic", ic.toString())
                sp.putString(localProfileNumbered + "isf", isf.toString())
                sp.putString(localProfileNumbered + "basal", basal.toString())
                sp.putString(localProfileNumbered + "targetlow", targetLow.toString())
                sp.putString(localProfileNumbered + "targethigh", targetHigh.toString())
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
            OKDialog.show(it, "", resourceHelper.gs(R.string.profilenamecontainsdot))
        }
    }

    @Synchronized
    fun loadSettings() {
        numOfProfiles = sp.getInt(Constants.LOCAL_PROFILE + "_profiles", 0)
        profiles.clear()
//        numOfProfiles = max(numOfProfiles, 1) // create at least one default profile if none exists

        for (i in 0 until numOfProfiles) {
            val p = SingleProfile()
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
        // create at least one profile if doesn't exist
        if (profiles.size < 1) profiles.add(defaultProfile())
        isEdited = false
        numOfProfiles = profiles.size
        createAndStoreConvertedProfile()
    }

    @Synchronized
    fun loadFromStore(store: ProfileStore) {
        try {
            val newProfiles: ArrayList<SingleProfile> = ArrayList()
            for (p in store.getProfileList()) {
                store.getSpecificProfile(p.toString())?.let {
                    val sp = copyFrom(it, p.toString())
                    sp.name = p.toString()
                    newProfiles.add(sp)
                }
            }
            if (newProfiles.size > 0) {
                profiles = newProfiles
                numOfProfiles = profiles.size
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

    private fun defaultProfile(): SingleProfile =
        SingleProfile().also { p ->
            p.name = Constants.LOCAL_PROFILE
            p.mgdl = profileFunction.getUnits() == GlucoseUnit.MGDL
            p.dia = Constants.defaultDIA
            try {
                p.ic = JSONArray(defaultArray)
                p.isf = JSONArray(defaultArray)
                p.basal = JSONArray(defaultArray)
                p.targetLow = JSONArray(defaultArray)
                p.targetHigh = JSONArray(defaultArray)
            } catch (e: JSONException) {
                aapsLogger.error("Exception", e)
            }
        }

    fun copyFrom(pureProfile: PureProfile, newName: String): SingleProfile {
        var verifiedName = newName
        if (rawProfile?.getSpecificProfile(newName) != null) {
            verifiedName += " " + dateUtil.now().toString()
        }
        val profile = ProfileSealed.Pure(pureProfile)
        val pureJson = pureProfile.jsonObject
        val sp = SingleProfile()
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
        val p = SingleProfile()
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
        numOfProfiles++
        createAndStoreConvertedProfile()
        storeSettings()
    }

    fun cloneProfile() {
        val p = profiles[currentProfileIndex].deepClone()
        p.name = p.name + " copy"
        profiles.add(p)
        currentProfileIndex = profiles.size - 1
        numOfProfiles++
        createAndStoreConvertedProfile()
        storeSettings()
        isEdited = false
    }

    fun addProfile(p: SingleProfile) {
        profiles.add(p)
        currentProfileIndex = profiles.size - 1
        numOfProfiles++
        createAndStoreConvertedProfile()
        storeSettings()
        isEdited = false
    }

    fun removeCurrentProfile() {
        profiles.removeAt(currentProfileIndex)
        numOfProfiles--
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
            if (numOfProfiles > 0) json.put("defaultProfile", currentProfile()?.name)
            val startDate = sp.getLong(R.string.key_local_profile_last_change, dateUtil.now())
            json.put("startDate", dateUtil.toISOAsUTC(startDate))
            json.put("store", store)
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }

        return ProfileStore(injector, json, dateUtil)
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
        @Inject lateinit var rxBus: RxBusWrapper
        @Inject lateinit var dateUtil: DateUtil
        @Inject lateinit var dataWorker: DataWorker
        @Inject lateinit var sp: SP
        @Inject lateinit var config: Config
        @Inject lateinit var localProfilePlugin: LocalProfilePlugin

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }

        override fun doWork(): Result {
            val profileJson = dataWorker.pickupJSONObject(inputData.getLong(DataWorker.STORE_KEY, -1))
                ?: return Result.failure(workDataOf("Error" to "missing input data"))
            if (sp.getBoolean(R.string.key_ns_receive_profile_store, false) || config.NSCLIENT) {
                val store = ProfileStore(injector, profileJson, dateUtil)
                val startDate = store.getStartDate()
                val lastLocalChange = sp.getLong(R.string.key_local_profile_last_change, 0)
                aapsLogger.debug(LTag.PROFILE, "Received profileStore: StartDate: $startDate Local last modification: $lastLocalChange")
                @Suppress("LiftReturnOrAssignment")
                if (startDate > lastLocalChange || startDate % 1000 == 0L) {// whole second means edited in NS
                    localProfilePlugin.loadFromStore(store)
                    aapsLogger.debug(LTag.PROFILE, "Received profileStore: $profileJson")
                    return Result.success(workDataOf("Data" to profileJson.toString().substring(0..min(5000, profileJson.length()))))
                } else
                    return Result.success(workDataOf("Result" to "Unchanged. Ignoring"))
            }
            return Result.success(workDataOf("Result" to "Sync not enabled"))
        }
    }

}
