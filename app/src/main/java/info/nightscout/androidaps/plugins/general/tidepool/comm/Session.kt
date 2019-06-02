package info.nightscout.androidaps.plugins.general.tidepool.comm

import info.nightscout.androidaps.plugins.general.tidepool.messages.AuthReplyMessage
import info.nightscout.androidaps.plugins.general.tidepool.messages.DatasetReplyMessage
import okhttp3.Headers

class Session (authHeader: String?, session_token_header: String) {
    var SESSION_TOKEN_HEADER: String
    var authHeader: String?

    val service = TidepoolUploader.getRetrofitInstance()?.create(TidepoolApiService::class.java)

    internal var token: String? = null
    internal var authReply: AuthReplyMessage? = null
    internal var datasetReply: DatasetReplyMessage? = null
    internal var start: Long = 0
    internal var end: Long = 0
    @Volatile
    internal var iterations: Int = 0


    init {
        this.authHeader = authHeader
        this.SESSION_TOKEN_HEADER = session_token_header
    }

    fun populateHeaders(headers: Headers) {
        if (this.token == null) {
            this.token = headers.get(SESSION_TOKEN_HEADER)
        }
    }

    fun populateBody(obj: Any?) {
        if (obj == null) return
        if (obj is AuthReplyMessage) {
            authReply = obj
        } else if (obj is List<*>) {
            val list = obj as List<*>?
            if (list!!.size > 0 && list[0] is DatasetReplyMessage) {
                datasetReply = list[0] as DatasetReplyMessage
            }
        } else if (obj is DatasetReplyMessage) {
            datasetReply = obj
        }
    }

    internal fun exceededIterations(): Boolean {
        return iterations > 50
    }

    fun authHeader(): String? {
        return authHeader;
    }

    fun service(): TidepoolApiService? {
        return service;
    }
}