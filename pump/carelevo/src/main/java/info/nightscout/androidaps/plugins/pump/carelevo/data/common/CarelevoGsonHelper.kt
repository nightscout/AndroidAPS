package info.nightscout.androidaps.plugins.pump.carelevo.data.common

import com.google.gson.Gson
import com.google.gson.GsonBuilder

object CarelevoGsonHelper {
    private var defaultGson : Gson? = null

    init {
        defaultGson = GsonBuilder()
            .serializeSpecialFloatingPointValues()
            .create()
    }

    fun sharedGson() : Gson {
        if(defaultGson == null) {
            throw RuntimeException("Not configure gson")
        }
        return defaultGson!!
    }
}