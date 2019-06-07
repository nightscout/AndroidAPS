package info.nightscout.androidaps.plugins.general.tidepool.messages

import info.nightscout.androidaps.plugins.general.tidepool.utils.GsonInstance
import okhttp3.MediaType
import okhttp3.RequestBody

open class BaseMessage {
    private fun toS(): String {
        return GsonInstance.defaultGsonInstance().toJson(this) ?: "null"
    }

    fun getBody(): RequestBody {
        return RequestBody.create(MediaType.parse("application/json"), this.toS())
    }

}