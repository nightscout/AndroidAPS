package info.nightscout.androidaps.interfaces

import androidx.collection.ArrayMap
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.utils.JsonHelper
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import javax.inject.Inject

class ProfileStore(val injector: HasAndroidInjector, val data: JSONObject) {
    @Inject lateinit var aapsLogger: AAPSLogger

    init {
        injector.androidInjector().inject(this)
    }

    private val cachedObjects = ArrayMap<String, Profile>()

    private fun getStore(): JSONObject? {
        try {
            if (data.has("store")) return data.getJSONObject("store")
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
        return null
    }

    fun getDefaultProfile(): Profile? = getDefaultProfileName()?.let { getSpecificProfile(it) }

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

    fun getSpecificProfile(profileName: String): Profile? {
        var profile: Profile? = null
        getStore()?.let { store ->
            if (store.has(profileName)) {
                profile = cachedObjects[profileName]
                if (profile == null) {
                    JsonHelper.safeGetJSONObject(store, profileName, null)?.let { profileObject ->
                        // take units from profile and if N/A from store
                        JsonHelper.safeGetStringAllowNull(profileObject, "units", JsonHelper.safeGetString(data, "units"))?.let { units ->
                            profile = Profile(injector, profileObject, units)
                            cachedObjects[profileName] = profile
                        }
                    }
                }
            }
        }
        return profile
    }
}
