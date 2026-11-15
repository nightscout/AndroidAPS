package app.aaps.plugins.sync.tidepool.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag

import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.io.IOException

class InfoInterceptor(val aapsLogger: AAPSLogger) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        request.body?.let { body ->
            // Lazy: the body is copied into a string only when the TIDEPOOL log is on. This interceptor
            // is always added, and the copy of every upload showed in a CPU trace.
            aapsLogger.debug(LTag.TIDEPOOL) { "Interceptor Body size: " + body.contentLength() }
            aapsLogger.debug(LTag.TIDEPOOL) {
                val requestBuffer = Buffer()
                body.writeTo(requestBuffer)
                "Interceptor Body: " + requestBuffer.readUtf8()
            }
        }
        return chain.proceed(request)
    }
}
