package info.nightscout.androidaps.plugins.general.tidepool.messages

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class AuthReplyMessage {

    @Expose
    @SerializedName("emailVerified")
    internal var emailVerified: Boolean? = null
    @Expose
    @SerializedName("emails")
    internal var emailList: List<String>? = null
    @Expose
    @SerializedName("termsAccepted")
    internal var termsDate: String? = null
    @Expose
    @SerializedName("userid")
    internal var userid: String? = null
    @Expose
    @SerializedName("username")
    internal var username: String? = null
}
