package app.aaps.plugins.sync.tidepool.messages

import app.aaps.core.keys.Preferences
import app.aaps.core.keys.StringKey
import okhttp3.Credentials

object AuthRequestMessage : BaseMessage() {

    fun getAuthRequestHeader(preferences: Preferences): String? {
        val username = preferences.get(StringKey.TidepoolUsername)
        val password = preferences.get(StringKey.TidepoolPassword)

        return if (username.isEmpty() || password.isEmpty()) null
        else Credentials.basic(username.trim { it <= ' ' }, password)
    }
}
