package app.aaps.core.interfaces.objects

import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.profile.ProfileStore
import org.json.JSONObject

interface Instantiator {

    fun provideProfileStore(jsonObject: JSONObject): ProfileStore
    fun provideAPSResultObject(): APSResult
    fun provideAutosensDataObject(): AutosensData
}