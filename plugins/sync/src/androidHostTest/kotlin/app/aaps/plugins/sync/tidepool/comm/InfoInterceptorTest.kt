package app.aaps.plugins.sync.tidepool.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import com.google.common.truth.Truth.assertThat
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [InfoInterceptor], which writes the body of every Tidepool request to the log. Reading the
 * body must not use it up, so the call has to go on with the same request.
 */
class InfoInterceptorTest {

    private val aapsLogger: AAPSLogger = mock()
    private val sut = InfoInterceptor(aapsLogger)

    private fun chainFor(request: Request): Pair<Interceptor.Chain, Response> {
        val response = Response.Builder()
            .request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body("".toResponseBody(null))
            .build()
        val chain: Interceptor.Chain = mock()
        whenever(chain.request()).thenReturn(request)
        whenever(chain.proceed(request)).thenReturn(response)
        return chain to response
    }

    @Test
    fun `body is logged and the request goes on`() {
        val body = """[{"type":"cbg"}]"""
        val request = Request.Builder()
            .url("https://api.tidepool.org/v1/datasets/1/data")
            .post(body.toRequestBody("application/json".toMediaTypeOrNull()))
            .build()
        val (chain, response) = chainFor(request)

        assertThat(sut.intercept(chain)).isEqualTo(response)
        verify(aapsLogger).debug(LTag.TIDEPOOL, "Interceptor Body size: ${body.length}")
        verify(aapsLogger).debug(LTag.TIDEPOOL, "Interceptor Body: $body")
    }

    @Test
    fun `request without a body is not logged`() {
        val request = Request.Builder().url("https://api.tidepool.org/v1/datasets").build()
        val (chain, response) = chainFor(request)

        assertThat(sut.intercept(chain)).isEqualTo(response)
        verify(aapsLogger, never()).debug(any<LTag>(), any<String>())
    }
}
