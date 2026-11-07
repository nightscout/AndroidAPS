package app.aaps.plugins.sync.tidepool.comm

import com.google.common.truth.Truth.assertThat
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

/**
 * Tests for [DeleteTimeoutInterceptor]: only a DELETE gets the long read timeout, every other call
 * goes on unchanged with the default one.
 */
class DeleteTimeoutInterceptorTest {

    private val sut = DeleteTimeoutInterceptor()

    private fun responseFor(request: Request): Response = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
        .body("".toResponseBody(null))
        .build()

    @Test
    fun `delete gets the long read timeout`() {
        val request = Request.Builder().url("https://api.tidepool.org/v1/datasets/1").delete().build()
        val response = responseFor(request)
        val chain: Interceptor.Chain = mock()
        val longChain: Interceptor.Chain = mock()
        whenever(chain.request()).thenReturn(request)
        whenever(chain.withReadTimeout(DeleteTimeoutInterceptor.DELETE_READ_TIMEOUT_MINUTES, TimeUnit.MINUTES)).thenReturn(longChain)
        whenever(longChain.proceed(request)).thenReturn(response)

        assertThat(sut.intercept(chain)).isEqualTo(response)
        verify(chain, never()).proceed(any())
    }

    @Test
    fun `other calls keep the default timeout`() {
        val request = Request.Builder().url("https://api.tidepool.org/v1/users/1/data_sets").build()
        val response = responseFor(request)
        val chain: Interceptor.Chain = mock()
        whenever(chain.request()).thenReturn(request)
        whenever(chain.proceed(request)).thenReturn(response)

        assertThat(sut.intercept(chain)).isEqualTo(response)
        verify(chain, never()).withReadTimeout(any(), any())
    }
}
