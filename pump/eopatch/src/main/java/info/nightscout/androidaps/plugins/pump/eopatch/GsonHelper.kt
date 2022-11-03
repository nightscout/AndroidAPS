package info.nightscout.androidaps.plugins.pump.eopatch

import com.google.gson.Gson
import com.google.gson.GsonBuilder

object GsonHelper {
    private var defaultGson: Gson? = null

    init {
        defaultGson = GsonBuilder().serializeSpecialFloatingPointValues().create()
    }

    fun sharedGson(): Gson {
        if (defaultGson == null) {
            throw RuntimeException("Not configured gson")
        }

        return defaultGson!!
    }
}
