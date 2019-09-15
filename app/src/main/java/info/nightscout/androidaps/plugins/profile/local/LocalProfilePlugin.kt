package info.nightscout.androidaps.plugins.profile.local

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.ProfileStore
import info.nightscout.androidaps.events.EventProfileStoreChanged
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.interfaces.ProfileInterface
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.SP
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.slf4j.LoggerFactory

/**
 * Created by mike on 05.08.2016.
 */
object LocalProfilePlugin : PluginBase(PluginDescription()
        .mainType(PluginType.PROFILE)
        .fragmentClass(LocalProfileFragment::class.java.name)
        .pluginName(R.string.localprofile)
        .shortName(R.string.localprofile_shortname)
        .description(R.string.description_profile_local)), ProfileInterface {

    private val log = LoggerFactory.getLogger(L.PROFILE)

    private var rawProfile: ProfileStore? = null

    const val LOCAL_PROFILE = "LocalProfile"

    private const val DEFAULTARRAY = "[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":0}]"

    var isEdited: Boolean = false
    internal var mgdl: Boolean = false
    internal var mmol: Boolean = false
    internal var dia: Double? = null
    internal var ic: JSONArray? = null
    internal var isf: JSONArray? = null
    internal var basal: JSONArray? = null
    internal var targetLow: JSONArray? = null
    internal var targetHigh: JSONArray? = null

    @Synchronized
    fun isValidEditState(): Boolean {
        return createProfileStore().defaultProfile?.isValid(MainApp.gs(R.string.localprofile), false)
                ?: false
    }

    init {
        loadSettings()
    }

    @Synchronized
    fun storeSettings() {
        SP.putBoolean(LOCAL_PROFILE + "mmol", mmol)
        SP.putBoolean(LOCAL_PROFILE + "mgdl", mgdl)
        SP.putString(LOCAL_PROFILE + "dia", dia.toString())
        SP.putString(LOCAL_PROFILE + "ic", ic.toString())
        SP.putString(LOCAL_PROFILE + "isf", isf.toString())
        SP.putString(LOCAL_PROFILE + "basal", basal.toString())
        SP.putString(LOCAL_PROFILE + "targetlow", targetLow.toString())
        SP.putString(LOCAL_PROFILE + "targethigh", targetHigh.toString())

        createAndStoreConvertedProfile()
        isEdited = false
        if (L.isEnabled(L.PROFILE))
            log.debug("Storing settings: " + rawProfile?.data.toString())
        RxBus.send(EventProfileStoreChanged())
    }

    @Synchronized
    fun loadSettings() {
        if (L.isEnabled(L.PROFILE))
            log.debug("Loading stored settings")

        mgdl = SP.getBoolean(LOCAL_PROFILE + "mgdl", false)
        mmol = SP.getBoolean(LOCAL_PROFILE + "mmol", true)
        dia = SP.getDouble(LOCAL_PROFILE + "dia", Constants.defaultDIA)
        try {
            ic = JSONArray(SP.getString(LOCAL_PROFILE + "ic", DEFAULTARRAY))
        } catch (e1: JSONException) {
            try {
                ic = JSONArray(DEFAULTARRAY)
            } catch (ignored: JSONException) {
            }

        }

        try {
            isf = JSONArray(SP.getString(LOCAL_PROFILE + "isf", DEFAULTARRAY))
        } catch (e1: JSONException) {
            try {
                isf = JSONArray(DEFAULTARRAY)
            } catch (ignored: JSONException) {
            }

        }

        try {
            basal = JSONArray(SP.getString(LOCAL_PROFILE + "basal", DEFAULTARRAY))
        } catch (e1: JSONException) {
            try {
                basal = JSONArray(DEFAULTARRAY)
            } catch (ignored: JSONException) {
            }

        }

        try {
            targetLow = JSONArray(SP.getString(LOCAL_PROFILE + "targetlow", DEFAULTARRAY))
        } catch (e1: JSONException) {
            try {
                targetLow = JSONArray(DEFAULTARRAY)
            } catch (ignored: JSONException) {
            }

        }

        try {
            targetHigh = JSONArray(SP.getString(LOCAL_PROFILE + "targethigh", DEFAULTARRAY))
        } catch (e1: JSONException) {
            try {
                targetHigh = JSONArray(DEFAULTARRAY)
            } catch (ignored: JSONException) {
            }

        }

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

    fun createProfileStore(): ProfileStore {
        val json = JSONObject()
        val store = JSONObject()
        val profile = JSONObject()

        try {
            json.put("defaultProfile", LOCAL_PROFILE)
            json.put("store", store)
            profile.put("dia", dia)
            profile.put("carbratio", ic)
            profile.put("sens", isf)
            profile.put("basal", basal)
            profile.put("target_low", targetLow)
            profile.put("target_high", targetHigh)
            profile.put("units", if (mgdl) Constants.MGDL else Constants.MMOL)
            store.put(LOCAL_PROFILE, profile)
        } catch (e: JSONException) {
            log.error("Unhandled exception", e)
        }

        return ProfileStore(json)
    }

    override fun getProfile(): ProfileStore? {
        return if (rawProfile?.defaultProfile?.isValid(MainApp.gs(R.string.localprofile)) != true) null else rawProfile
    }

    override fun getUnits(): String {
        return if (mgdl) Constants.MGDL else Constants.MMOL
    }

    override fun getProfileName(): String {
        return DecimalFormatter.to2Decimal(rawProfile?.defaultProfile?.percentageBasalSum()
                ?: 0.0) + "U "
    }

}
