package info.nightscout.androidaps.data

import androidx.collection.ArrayMap
import org.json.JSONException
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.util.*

class ProfileStore(val data: JSONObject) {
    private val log = LoggerFactory.getLogger(ProfileStore::class.java)

    private val cachedObjects = ArrayMap<String, Profile>()

    private fun getStore(): JSONObject? {
        try {
            if (data.has("store")) return data.getJSONObject("store")
        } catch (e: JSONException) {
            log.error("Unhandled exception", e)
        }
        return null
    }

    fun getDefaultProfile(): Profile? = getDefaultProfileName()?.let { getSpecificProfile(it) }

    fun getDefaultProfileName(): String? {
        val defaultProfileName = data.getString("defaultProfile")
        return getStore()?.has(defaultProfileName)?.let { defaultProfileName }
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
        try {
            getStore()?.let { store ->
                if (store.has(profileName)) {
                    profile = cachedObjects[profileName]
                    if (profile == null) {
                        val profileObject = store.getJSONObject(profileName)
                        if (profileObject != null && profileObject.has("units")) {
                            profile = Profile(profileObject, profileObject.getString("units"))
                            cachedObjects[profileName] = profile
                        }
                    }
                }
            }
        } catch (e: JSONException) {
            log.error("Unhandled exception", e)
        }
        return profile
    }
}
