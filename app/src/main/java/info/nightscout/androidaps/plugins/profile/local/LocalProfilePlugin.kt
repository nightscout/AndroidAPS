package info.nightscout.androidaps.plugins.profile.local

import android.app.Activity
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.interfaces.ProfileStore
import info.nightscout.androidaps.events.EventProfileStoreChanged
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.interfaces.ProfileInterface
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.ArrayList
import kotlin.math.max

@Singleton
class LocalProfilePlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    private val rxBus: RxBusWrapper,
    resourceHelper: ResourceHelper,
    private val sp: SP,
    private val profileFunction: ProfileFunction,
    private val nsUpload: NSUpload
) : PluginBase(PluginDescription()
    .mainType(PluginType.PROFILE)
    .fragmentClass(LocalProfileFragment::class.java.name)
    .enableByDefault(true)
    .pluginName(R.string.localprofile)
    .shortName(R.string.localprofile_shortname)
    .description(R.string.description_profile_local)
    .setDefault(),
    aapsLogger, resourceHelper, injector
), ProfileInterface {

    var rawProfile: ProfileStore? = null

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

        fun copyFrom(rawProfile: ProfileStore?, profile: Profile, newName: String): SingleProfile {
            var verifiedName = newName
            if (rawProfile?.getSpecificProfile(newName) != null) {
                verifiedName += " " + DateUtil.now().toString()
            }
            val sp = SingleProfile()
            sp.name = verifiedName
            sp.mgdl = profile.units == Constants.MGDL
            sp.dia = profile.dia
            sp.ic = JSONArray(profile.data.getJSONArray("carbratio").toString())
            sp.isf = JSONArray(profile.data.getJSONArray("sens").toString())
            sp.basal = JSONArray(profile.data.getJSONArray("basal").toString())
            sp.targetLow = JSONArray(profile.data.getJSONArray("target_low").toString())
            sp.targetHigh = JSONArray(profile.data.getJSONArray("target_high").toString())
            return sp
        }
    }

    var isEdited: Boolean = false
    var profiles: ArrayList<SingleProfile> = ArrayList()

    var numOfProfiles = 0
    internal var currentProfileIndex = 0

    fun currentProfile() = profiles[currentProfileIndex]

    @Synchronized
    fun isValidEditState(): Boolean {
        return createProfileStore().getDefaultProfile()?.isValid(resourceHelper.gs(R.string.localprofile), false)
            ?: false
    }

    @Synchronized
    fun storeSettings(activity: Activity? = null) {
        for (i in 0 until numOfProfiles) {
            profiles[i].run {
                val LOCAL_PROFILE_NUMBERED = Constants.LOCAL_PROFILE + "_" + i + "_"
                sp.putString(LOCAL_PROFILE_NUMBERED + "name", name!!)
                sp.putBoolean(LOCAL_PROFILE_NUMBERED + "mgdl", mgdl)
                sp.putDouble(LOCAL_PROFILE_NUMBERED + "dia", dia)
                sp.putString(LOCAL_PROFILE_NUMBERED + "ic", ic.toString())
                sp.putString(LOCAL_PROFILE_NUMBERED + "isf", isf.toString())
                sp.putString(LOCAL_PROFILE_NUMBERED + "basal", basal.toString())
                sp.putString(LOCAL_PROFILE_NUMBERED + "targetlow", targetLow.toString())
                sp.putString(LOCAL_PROFILE_NUMBERED + "targethigh", targetHigh.toString())
            }
        }
        sp.putInt(Constants.LOCAL_PROFILE + "_profiles", numOfProfiles)

        createAndStoreConvertedProfile()
        isEdited = false
        aapsLogger.debug(LTag.PROFILE, "Storing settings: " + rawProfile?.data.toString())
        rxBus.send(EventProfileStoreChanged())
        var namesOK = true
        profiles.forEach {
            val name = it.name ?: "."
            if (name.contains(".")) namesOK = false
        }
        if (namesOK)
            rawProfile?.let { nsUpload.uploadProfileStore(it.data) }
        else
            activity?.let {
                OKDialog.show(it, "", resourceHelper.gs(R.string.profilenamecontainsdot))
            }
    }

    @Synchronized
    fun loadSettings() {
        if (sp.contains(Constants.LOCAL_PROFILE + "mgdl")) {
            doConversion()
            return
        }

        numOfProfiles = sp.getInt(Constants.LOCAL_PROFILE + "_profiles", 0)
        profiles.clear()
        numOfProfiles = max(numOfProfiles, 1) // create at least one default profile if none exists

        for (i in 0 until numOfProfiles) {
            val p = SingleProfile()
            val LOCAL_PROFILE_NUMBERED = Constants.LOCAL_PROFILE + "_" + i + "_"

            p.name = sp.getString(LOCAL_PROFILE_NUMBERED + "name", Constants.LOCAL_PROFILE + i)
            if (isExistingName(p.name)) continue
            p.mgdl = sp.getBoolean(LOCAL_PROFILE_NUMBERED + "mgdl", false)
            p.dia = sp.getDouble(LOCAL_PROFILE_NUMBERED + "dia", Constants.defaultDIA)
            try {
                p.ic = JSONArray(sp.getString(LOCAL_PROFILE_NUMBERED + "ic", defaultArray))
            } catch (e1: JSONException) {
                try {
                    p.ic = JSONArray(defaultArray)
                } catch (ignored: JSONException) {
                }
                aapsLogger.error("Exception", e1)
            }

            try {
                p.isf = JSONArray(sp.getString(LOCAL_PROFILE_NUMBERED + "isf", defaultArray))
            } catch (e1: JSONException) {
                try {
                    p.isf = JSONArray(defaultArray)
                } catch (ignored: JSONException) {
                }
                aapsLogger.error("Exception", e1)
            }

            try {
                p.basal = JSONArray(sp.getString(LOCAL_PROFILE_NUMBERED + "basal", defaultArray))
            } catch (e1: JSONException) {
                try {
                    p.basal = JSONArray(defaultArray)
                } catch (ignored: JSONException) {
                }
                aapsLogger.error("Exception", e1)
            }

            try {
                p.targetLow = JSONArray(sp.getString(LOCAL_PROFILE_NUMBERED + "targetlow", defaultArray))
            } catch (e1: JSONException) {
                try {
                    p.targetLow = JSONArray(defaultArray)
                } catch (ignored: JSONException) {
                }
                aapsLogger.error("Exception", e1)
            }

            try {
                p.targetHigh = JSONArray(sp.getString(LOCAL_PROFILE_NUMBERED + "targethigh", defaultArray))
            } catch (e1: JSONException) {
                try {
                    p.targetHigh = JSONArray(defaultArray)
                } catch (ignored: JSONException) {
                }
                aapsLogger.error("Exception", e1)
            }

            profiles.add(p)
        }
        isEdited = false
        numOfProfiles = profiles.size
        createAndStoreConvertedProfile()
    }

    private fun isExistingName(name: String?): Boolean {
        for (p in profiles) {
            if (p.name == name) return true
        }
        return false
    }

    @Synchronized
    private fun doConversion() { // conversion from 2.3 to 2.4 format
        aapsLogger.debug(LTag.PROFILE, "Loading stored settings")
        val p = SingleProfile()

        p.mgdl = sp.getBoolean(Constants.LOCAL_PROFILE + "mgdl", profileFunction.getUnits() == Constants.MGDL)
        p.dia = sp.getDouble(Constants.LOCAL_PROFILE + "dia", Constants.defaultDIA)
        try {
            p.ic = JSONArray(sp.getString(Constants.LOCAL_PROFILE + "ic", defaultArray))
        } catch (e1: JSONException) {
            try {
                p.ic = JSONArray(defaultArray)
            } catch (ignored: JSONException) {
            }
        }

        try {
            p.isf = JSONArray(sp.getString(Constants.LOCAL_PROFILE + "isf", defaultArray))
        } catch (e1: JSONException) {
            try {
                p.isf = JSONArray(defaultArray)
            } catch (ignored: JSONException) {
            }
        }

        try {
            p.basal = JSONArray(sp.getString(Constants.LOCAL_PROFILE + "basal", defaultArray))
        } catch (e1: JSONException) {
            try {
                p.basal = JSONArray(defaultArray)
            } catch (ignored: JSONException) {
            }
        }

        try {
            p.targetLow = JSONArray(sp.getString(Constants.LOCAL_PROFILE + "targetlow", defaultArray))
        } catch (e1: JSONException) {
            try {
                p.targetLow = JSONArray(defaultArray)
            } catch (ignored: JSONException) {
            }
        }

        try {
            p.targetHigh = JSONArray(sp.getString(Constants.LOCAL_PROFILE + "targethigh", defaultArray))
        } catch (e1: JSONException) {
            try {
                p.targetHigh = JSONArray(defaultArray)
            } catch (ignored: JSONException) {
            }
        }
        p.name = Constants.LOCAL_PROFILE

        sp.remove(Constants.LOCAL_PROFILE + "mgdl")
        sp.remove(Constants.LOCAL_PROFILE + "mmol")
        sp.remove(Constants.LOCAL_PROFILE + "dia")
        sp.remove(Constants.LOCAL_PROFILE + "ic")
        sp.remove(Constants.LOCAL_PROFILE + "isf")
        sp.remove(Constants.LOCAL_PROFILE + "basal")
        sp.remove(Constants.LOCAL_PROFILE + "targetlow")
        sp.remove(Constants.LOCAL_PROFILE + "targethigh")

        currentProfileIndex = 0
        numOfProfiles = 1
        profiles.clear()
        profiles.add(p)
        storeSettings()

        isEdited = false
        createAndStoreConvertedProfile()
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
        p.mgdl = profileFunction.getUnits() == Constants.MGDL
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
            json.put("defaultProfile", currentProfile().name)
            json.put("startDate", DateUtil.toISOAsUTC(DateUtil.now()))
            json.put("store", store)
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }

        return ProfileStore(injector, json)
    }

    override fun getProfile(): ProfileStore? {
        return rawProfile
    }

    override fun getProfileName(): String {
        return DecimalFormatter.to2Decimal(rawProfile?.getDefaultProfile()?.percentageBasalSum()
            ?: 0.0) + "U "
    }
}
