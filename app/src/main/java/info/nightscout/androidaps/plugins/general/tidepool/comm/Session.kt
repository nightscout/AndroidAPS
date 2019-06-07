package info.nightscout.androidaps.plugins.general.tidepool.comm

import info.nightscout.androidaps.plugins.general.tidepool.messages.AuthReplyMessage
import info.nightscout.androidaps.plugins.general.tidepool.messages.DatasetReplyMessage
import okhttp3.Headers

class Session (var authHeader: String?, private var sessionTokenHeader: String) {

    val service = TidepoolUploader.getRetrofitInstance()?.create(TidepoolApiService::class.java)

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
            val list = obj as List<*>?
            if (list!!.isNotEmpty() && list[0] is DatasetReplyMessage) {
                datasetReply = list[0] as DatasetReplyMessage
            }
        } else if (obj is DatasetReplyMessage) {
            datasetReply = obj
        }
    }
}