package app.aaps.core.interfaces.objects

import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.pump.PumpEnactResult
import org.json.JSONObject

interface Instantiator {

    fun provideProfileStore(jsonObject: JSONObject): ProfileStore
    fun provideAPSResultObject(rt: RT): APSResult
    fun provideAutosensDataObject(): AutosensData
    fun providePumpEnactResult(): PumpEnactResult
}