package info.nightscout.androidaps.plugins.general.tidepool.messages

import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.StringUtils
import info.nightscout.androidaps.utils.sharedPreferences.SP
import okhttp3.Credentials

class AuthRequestMessage : BaseMessage() {
    companion object {
        fun getAuthRequestHeader(sp: SP): String? {
            val username = sp.getStringOrNull(R.string.key_tidepool_username, null)
            val password = sp.getStringOrNull(R.string.key_tidepool_password, null)

            return if (StringUtils.emptyString(username) || StringUtils.emptyString(password)) null else Credentials.basic(username!!.trim { it <= ' ' }, password!!)
        }
    }
}
