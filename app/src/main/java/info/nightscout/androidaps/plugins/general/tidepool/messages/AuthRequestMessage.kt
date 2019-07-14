package info.nightscout.androidaps.plugins.general.tidepool.messages

import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.SP
import info.nightscout.androidaps.utils.StringUtils
import okhttp3.Credentials

class AuthRequestMessage : BaseMessage() {
    companion object {
        fun getAuthRequestHeader(): String? {
            val username = SP.getString(R.string.key_tidepool_username, null)
            val password = SP.getString(R.string.key_tidepool_password, null)

            return if (StringUtils.emptyString(username) || StringUtils.emptyString(password)) null else Credentials.basic(username.trim { it <= ' ' }, password)
        }
    }
}
