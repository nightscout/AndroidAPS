package info.nightscout.androidaps.interfaces

import androidx.collection.ArrayMap
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.PureProfile
import info.nightscout.androidaps.extensions.pureProfileFromJson
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import javax.inject.Inject

class ProfileStore(val injector: HasAndroidInjector, val data: JSONObject, val dateUtil: DateUtil) {

    @Inject lateinit var aapsLogger: AAPSLogger

    init {
        injector.androidInjector().inject(this)
    }

    private val cachedObjects = ArrayMap<String, PureProfile>()

    private fun getStore(): JSONObject? {
        try {
            if (data.has("store")) return data.getJSONObject("store")
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
        return null
    }

    fun getStartDate(): Long {
        val iso = JsonHelper.safeGetString(data, "startDate") ?: return 0
        return try {
            dateUtil.fromISODateString(iso)
        } catch (e: Exception) {
            0
        }
    }

    fun getDefaultProfile(): PureProfile? = getDefaultProfileName()?.let { getSpecificProfile(it) }
    fun getDefaultProfileJson(): JSONObject? = getDefaultProfileName()?.let { getSpecificProfileJson(it) }

    fun getDefaultProfileName(): String? {
        val defaultProfileName = data.optString("defaultProfile")
        return if (defaultProfileName.isNotEmpty()) getStore()?.has(defaultProfileName)?.let { defaultProfileName } else null
    }

    fun getProfileList(): ArrayList<CharSequence> {
        val ret = ArrayList<CharSequence>()
        getStore()?.keys()?.let { keys ->
            while (keys.hasNext()) {
                val profileName = keys.next() as String
                ret.add(profileName)
            }
        }
        return ret
    }

    fun getSpecificProfile(profileName: String): PureProfile? {
        var profile: PureProfile? = null
        val defaultUnits = JsonHelper.safeGetStringAllowNull(data, "units", null)
        getStore()?.let { store ->
            if (store.has(profileName)) {
                profile = cachedObjects[profileName]
                if (profile == null) {
                    JsonHelper.safeGetJSONObject(store, profileName, null)?.let { profileObject ->
                        profile = pureProfileFromJson(profileObject, dateUtil, defaultUnits)
                        cachedObjects[profileName] = profile
                    }
                }
            }
        }
        return profile
    }

    private fun getSpecificProfileJson(profileName: String): JSONObject? {
        getStore()?.let { store ->
            if (store.has(profileName))
                return JsonHelper.safeGetJSONObject(store, profileName, null)
        }
        return null
    }
}
