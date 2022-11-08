package info.nightscout.androidaps.plugins.sync.tidepool.messages

import info.nightscout.androidaps.R
import info.nightscout.shared.sharedPreferences.SP
import okhttp3.Credentials

object AuthRequestMessage : BaseMessage() {

    fun getAuthRequestHeader(sp: SP): String? {
        val username = sp.getStringOrNull(R.string.key_tidepool_username, null)
        val password = sp.getStringOrNull(R.string.key_tidepool_password, null)

        return if (username.isNullOrEmpty() || password.isNullOrEmpty()) null
        else Credentials.basic(username.trim { it <= ' ' }, password)
    }
}
