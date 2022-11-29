package info.nightscout.plugins.sync.tidepool.comm

import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag

import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.io.IOException

class InfoInterceptor(val tag: String = "interceptor", val aapsLogger: AAPSLogger) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        request.body?.let {
            aapsLogger.debug(LTag.TIDEPOOL, "Interceptor Body size: " + it.contentLength())
            val requestBuffer = Buffer()
            it.writeTo(requestBuffer)
            aapsLogger.debug(LTag.TIDEPOOL, "Interceptor Body: " + requestBuffer.readUtf8())
        }
        return chain.proceed(request)
    }
}
