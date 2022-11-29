package info.nightscout.interfaces.profile

import org.json.JSONObject

interface ProfileInstantiator {
    fun storeInstance(jsonObject: JSONObject): ProfileStore
}