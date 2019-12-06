package info.nightscout.androidaps.plugins.general.tidepool.comm

import info.nightscout.androidaps.logging.L
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import org.slf4j.LoggerFactory
import java.io.IOException

class InfoInterceptor(tag: String) : Interceptor {

    private val log = LoggerFactory.getLogger(L.TIDEPOOL)
    private var tag = "interceptor"

    init {
        this.tag = tag
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        request.body?.let {
            if (L.isEnabled(L.TIDEPOOL)) {
                log.debug("Interceptor Body size: " + it.contentLength())
                val requestBuffer = Buffer()
                it.writeTo(requestBuffer)
                log.debug("Interceptor Body: " + requestBuffer.readUtf8())
            }
        }
        return chain.proceed(request)
    }
}
