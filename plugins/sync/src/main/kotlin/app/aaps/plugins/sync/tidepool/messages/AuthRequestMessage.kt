package app.aaps.plugins.sync.tidepool.messages

import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.plugins.sync.R
import okhttp3.Credentials

object AuthRequestMessage : BaseMessage() {

    fun getAuthRequestHeader(sp: SP): String? {
        val username = sp.getStringOrNull(R.string.key_tidepool_username, null)
        val password = sp.getStringOrNull(R.string.key_tidepool_password, null)

        return if (username.isNullOrEmpty() || password.isNullOrEmpty()) null
        else Credentials.basic(username.trim { it <= ' ' }, password)
    }
}
