package info.nightscout.androidaps.plugins.general.tidepool.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder

object GsonInstance {
    private var gson_instance: Gson? = null

    fun defaultGsonInstance(): Gson {
        if (gson_instance == null) {
            gson_instance = GsonBuilder()
                    .excludeFieldsWithoutExposeAnnotation()
                    .create()
        }
        return gson_instance as Gson
    }
}