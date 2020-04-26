package info.nightscout.androidaps.plugins.profile.local

import android.app.Activity
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.data.ProfileStore
import info.nightscout.androidaps.events.EventProfileStoreChanged
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.interfaces.ProfileInterface
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.OKDialog
import info.nightscout.androidaps.utils.SP
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.collections.ArrayList

object LocalProfilePlugin : PluginBase(PluginDescription()
    .mainType(PluginType.PROFILE)
    .fragmentClass(LocalProfileFragment::class.java.name)
    .pluginName(R.string.localprofile)
    .shortName(R.string.localprofile_shortname)
    .description(R.string.description_profile_local)), ProfileInterface {

    override fun onStart() {
        super.onStart()
        loadSettings()
    }

    private val log = LoggerFactory.getLogger(L.PROFILE)

    private var rawProfile: ProfileStore? = null

    const val LOCAL_PROFILE = "LocalProfile"

    private const val DEFAULTARRAY = "[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":0}]"

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

        fun copyFrom(profile: Profile, newName: String): SingleProfile {
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

    internal var numOfProfiles = 0
    internal var currentProfileIndex = 0

    fun currentProfile() = profiles[currentProfileIndex]

    @Synchronized
    fun isValidEditState(): Boolean {
        return createProfileStore().getDefaultProfile()?.isValid(MainApp.gs(R.string.localprofile), false)
            ?: false
    }

    @Synchronized
    fun storeSettings(activity: Activity? = null) {
        for (i in 0 until numOfProfiles) {
            profiles[i].run {
                val LOCAL_PROFILE_NUMBERED = LOCAL_PROFILE + "_" + i + "_"
                SP.putString(LOCAL_PROFILE_NUMBERED + "name", name)
                SP.putBoolean(LOCAL_PROFILE_NUMBERED + "mgdl", mgdl)
                SP.putDouble(LOCAL_PROFILE_NUMBERED + "dia", dia)
                SP.putString(LOCAL_PROFILE_NUMBERED + "ic", ic.toString())
                SP.putString(LOCAL_PROFILE_NUMBERED + "isf", isf.toString())
                SP.putString(LOCAL_PROFILE_NUMBERED + "basal", basal.toString())
                SP.putString(LOCAL_PROFILE_NUMBERED + "targetlow", targetLow.toString())
                SP.putString(LOCAL_PROFILE_NUMBERED + "targethigh", targetHigh.toString())
            }
        }
        SP.putInt(LOCAL_PROFILE + "_profiles", numOfProfiles)

        createAndStoreConvertedProfile()
        isEdited = false
        if (L.isEnabled(L.PROFILE))
            log.debug("Storing settings: " + rawProfile?.data.toString())
        RxBus.send(EventProfileStoreChanged())
        var namesOK = true
        profiles.forEach {
            val name = it.name ?: "."
            if (name.contains(".")) namesOK = false
        }
        if (namesOK)
            rawProfile?.let { NSUpload.uploadProfileStore(it.data) }
        else
            activity?.let {
                OKDialog.show(it, "", MainApp.gs(R.string.profilenamecontainsdot))
            }
    }

    @Synchronized
    fun loadSettings() {
        if (SP.contains(LOCAL_PROFILE + "mgdl")) {
            doConversion()
            return
        }

        numOfProfiles = SP.getInt(LOCAL_PROFILE + "_profiles", 0)
        profiles.clear()
        numOfProfiles = Math.max(numOfProfiles, 1) // create at least one default profile if none exists

        for (i in 0 until numOfProfiles) {
            val p = SingleProfile()
            val LOCAL_PROFILE_NUMBERED = LOCAL_PROFILE + "_" + i + "_"

            p.name = SP.getString(LOCAL_PROFILE_NUMBERED + "name", LOCAL_PROFILE + i)
            if (isExistingName(p.name)) continue
            p.mgdl = SP.getBoolean(LOCAL_PROFILE_NUMBERED + "mgdl", false)
            p.dia = SP.getDouble(LOCAL_PROFILE_NUMBERED + "dia", Constants.defaultDIA)
            try {
                p.ic = JSONArray(SP.getString(LOCAL_PROFILE_NUMBERED + "ic", DEFAULTARRAY))
            } catch (e1: JSONException) {
                try {
                    p.ic = JSONArray(DEFAULTARRAY)
                } catch (ignored: JSONException) {
                }
                log.error("Exception", e1)
            }

            try {
                p.isf = JSONArray(SP.getString(LOCAL_PROFILE_NUMBERED + "isf", DEFAULTARRAY))
            } catch (e1: JSONException) {
                try {
                    p.isf = JSONArray(DEFAULTARRAY)
                } catch (ignored: JSONException) {
                }
                log.error("Exception", e1)
            }

            try {
                p.basal = JSONArray(SP.getString(LOCAL_PROFILE_NUMBERED + "basal", DEFAULTARRAY))
            } catch (e1: JSONException) {
                try {
                    p.basal = JSONArray(DEFAULTARRAY)
                } catch (ignored: JSONException) {
                }
                log.error("Exception", e1)
            }

            try {
                p.targetLow = JSONArray(SP.getString(LOCAL_PROFILE_NUMBERED + "targetlow", DEFAULTARRAY))
            } catch (e1: JSONException) {
                try {
                    p.targetLow = JSONArray(DEFAULTARRAY)
                } catch (ignored: JSONException) {
                }
                log.error("Exception", e1)
            }

            try {
                p.targetHigh = JSONArray(SP.getString(LOCAL_PROFILE_NUMBERED + "targethigh", DEFAULTARRAY))
            } catch (e1: JSONException) {
                try {
                    p.targetHigh = JSONArray(DEFAULTARRAY)
                } catch (ignored: JSONException) {
                }
                log.error("Exception", e1)
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
        if (L.isEnabled(L.PROFILE))
            log.debug("Loading stored settings")
        val p = SingleProfile()

        p.mgdl = SP.getBoolean(LOCAL_PROFILE + "mgdl", ProfileFunctions.getSystemUnits() == Constants.MGDL)
        p.dia = SP.getDouble(LOCAL_PROFILE + "dia", Constants.defaultDIA)
        try {
            p.ic = JSONArray(SP.getString(LOCAL_PROFILE + "ic", DEFAULTARRAY))
        } catch (e1: JSONException) {
            try {
                p.ic = JSONArray(DEFAULTARRAY)
            } catch (ignored: JSONException) {
            }
        }

        try {
            p.isf = JSONArray(SP.getString(LOCAL_PROFILE + "isf", DEFAULTARRAY))
        } catch (e1: JSONException) {
            try {
                p.isf = JSONArray(DEFAULTARRAY)
            } catch (ignored: JSONException) {
            }
        }

        try {
            p.basal = JSONArray(SP.getString(LOCAL_PROFILE + "basal", DEFAULTARRAY))
        } catch (e1: JSONException) {
            try {
                p.basal = JSONArray(DEFAULTARRAY)
            } catch (ignored: JSONException) {
            }
        }

        try {
            p.targetLow = JSONArray(SP.getString(LOCAL_PROFILE + "targetlow", DEFAULTARRAY))
        } catch (e1: JSONException) {
            try {
                p.targetLow = JSONArray(DEFAULTARRAY)
            } catch (ignored: JSONException) {
            }
        }

        try {
            p.targetHigh = JSONArray(SP.getString(LOCAL_PROFILE + "targethigh", DEFAULTARRAY))
        } catch (e1: JSONException) {
            try {
                p.targetHigh = JSONArray(DEFAULTARRAY)
            } catch (ignored: JSONException) {
            }
        }
        p.name = LOCAL_PROFILE

        SP.remove(LOCAL_PROFILE + "mgdl")
        SP.remove(LOCAL_PROFILE + "mmol")
        SP.remove(LOCAL_PROFILE + "dia")
        SP.remove(LOCAL_PROFILE + "ic")
        SP.remove(LOCAL_PROFILE + "isf")
        SP.remove(LOCAL_PROFILE + "basal")
        SP.remove(LOCAL_PROFILE + "targetlow")
        SP.remove(LOCAL_PROFILE + "targethigh")

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
            if (rawProfile?.getSpecificProfile(LOCAL_PROFILE + i) == null) {
                free = i;
                break
            }
        }
        val p = SingleProfile()
        p.name = LOCAL_PROFILE + free
        p.mgdl = ProfileFunctions.getSystemUnits() == Constants.MGDL
        p.dia = Constants.defaultDIA
        p.ic = JSONArray(DEFAULTARRAY)
        p.isf = JSONArray(DEFAULTARRAY)
        p.basal = JSONArray(DEFAULTARRAY)
        p.targetLow = JSONArray(DEFAULTARRAY)
        p.targetHigh = JSONArray(DEFAULTARRAY)
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
            log.error("Unhandled exception", e)
        }

        return ProfileStore(json)
    }

    override fun getProfile(): ProfileStore? {
        return rawProfile
    }

    override fun getProfileName(): String {
        return DecimalFormatter.to2Decimal(rawProfile?.getDefaultProfile()?.percentageBasalSum()
            ?: 0.0) + "U "
    }

}
