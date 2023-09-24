package app.aaps.interfaces.objects

import app.aaps.interfaces.aps.APSResult
import app.aaps.interfaces.aps.AutosensData
import app.aaps.interfaces.profile.ProfileStore
import org.json.JSONObject

interface Instantiator {

    fun provideProfileStore(jsonObject: JSONObject): ProfileStore
    fun provideAPSResultObject(): APSResult
    fun provideAutosensDataObject(): AutosensData
}