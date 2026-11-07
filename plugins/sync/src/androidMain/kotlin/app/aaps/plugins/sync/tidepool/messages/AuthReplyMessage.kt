package app.aaps.plugins.sync.tidepool.messages

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class AuthReplyMessage {

    @Expose
    @SerializedName("userid")
    internal var userid: String? = null
}
