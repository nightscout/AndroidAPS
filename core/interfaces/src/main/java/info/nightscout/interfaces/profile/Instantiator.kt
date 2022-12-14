package info.nightscout.interfaces.profile

import info.nightscout.interfaces.aps.APSResult
import info.nightscout.interfaces.aps.AutosensData
import org.json.JSONObject

interface Instantiator {

    fun provideProfileStore(jsonObject: JSONObject): ProfileStore
    fun provideAPSResultObject(): APSResult
    fun provideAutosensDataObject(): AutosensData
}