package info.nightscout.androidaps.plugins.general.tidepool.comm

import info.nightscout.androidaps.plugins.general.tidepool.messages.AuthReplyMessage
import info.nightscout.androidaps.plugins.general.tidepool.messages.DatasetReplyMessage
import okhttp3.Headers

class Session(val authHeader: String?,
              private val sessionTokenHeader: String,
              val service: TidepoolApiService?) {

    internal var token: String? = null
    internal var authReply: AuthReplyMessage? = null
    internal var datasetReply: DatasetReplyMessage? = null
    internal var start: Long = 0
    internal var end: Long = 0
    @Volatile
    internal var iterations: Int = 0


    fun populateHeaders(headers: Headers) {
        if (this.token == null) {
            this.token = headers.get(sessionTokenHeader)
        }
    }

    fun populateBody(obj: Any?) {
        if (obj == null) return
        if (obj is AuthReplyMessage) {
            authReply = obj
        } else if (obj is List<*>) {
            val list = obj as? List<*>?

            list?.getOrNull(0)?.let {
                if (it is DatasetReplyMessage) {
                    datasetReply = it
                }
            }
        } else if (obj is DatasetReplyMessage) {
            datasetReply = obj
        }
    }
}