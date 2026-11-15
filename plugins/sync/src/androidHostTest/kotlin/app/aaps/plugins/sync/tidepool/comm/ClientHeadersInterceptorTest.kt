package app.aaps.plugins.sync.tidepool.comm

import app.aaps.core.interfaces.configuration.Config
import com.google.common.truth.Truth.assertThat
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests for [ClientHeadersInterceptor]: every Tidepool request names the app and its version. */
class ClientHeadersInterceptorTest {

    private val config: Config = mock()
    private val sut = ClientHeadersInterceptor(config)

    /** Runs the interceptor and returns the request it passed on. */
    private fun sentRequest(): Request {
        val request = Request.Builder().url("https://api.tidepool.org/v1/datasets/1/data").build()
        val chain: Interceptor.Chain = mock()
        whenever(chain.request()).thenReturn(request)
        whenever(chain.proceed(any())).thenAnswer { invocation ->
            Response.Builder()
                .request(invocation.getArgument(0)).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("".toResponseBody(null))
                .build()
        }
        sut.intercept(chain)
        val sent = argumentCaptor<Request>()
        verify(chain).proceed(sent.capture())
        return sent.firstValue
    }

    @Test
    fun `request carries the app id and version`() {
        whenever(config.APPLICATION_ID).thenReturn("info.nightscout.androidaps")
        whenever(config.VERSION_NAME).thenReturn("4.0.0")

        val request = sentRequest()

        assertThat(request.header("X-Tidepool-Client-Name")).isEqualTo("info.nightscout.androidaps")
        assertThat(request.header("X-Tidepool-Client-Version")).isEqualTo("4.0.0")
        assertThat(request.header("User-Agent")).isEqualTo("AAPS/4.0.0")
    }

    @Test
    fun `characters OkHttp refuses in a header are dropped`() {
        // A header value outside printable ASCII would make OkHttp throw, and so fail every request
        whenever(config.APPLICATION_ID).thenReturn("info.nightscout.androidaps")
        whenever(config.VERSION_NAME).thenReturn("4.0.0-dév\n")

        assertThat(sentRequest().header("X-Tidepool-Client-Version")).isEqualTo("4.0.0-dv")
    }
}
