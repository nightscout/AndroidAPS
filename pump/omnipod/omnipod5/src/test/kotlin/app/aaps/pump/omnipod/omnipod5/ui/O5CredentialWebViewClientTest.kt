package app.aaps.pump.omnipod.omnipod5.ui

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.IOException

class O5CredentialWebViewClientTest {

    @Test
    fun `main page network errors request the internet dialog`() {
        val codes = listOf(
            WebViewClient.ERROR_HOST_LOOKUP,
            WebViewClient.ERROR_CONNECT,
            WebViewClient.ERROR_IO,
            WebViewClient.ERROR_TIMEOUT
        )
        for (code in codes) {
            val failures = mutableListOf<Throwable>()
            val client = O5CredentialWebViewClient { failures.add(it) }

            client.onReceivedError(null, request(isMainFrame = true), error(code))

            assertThat(failures).hasSize(1)
            assertThat(failures.single()).isInstanceOf(O5CertificateNetworkException::class.java)
            assertThat(failures.single().message).isEqualTo("Page load failed")
        }
    }

    @Test
    fun `subresource errors do not interrupt the certificate page`() {
        val failures = mutableListOf<Throwable>()
        val client = O5CredentialWebViewClient { failures.add(it) }

        client.onReceivedError(null, request(isMainFrame = false), error(WebViewClient.ERROR_HOST_LOOKUP))

        assertThat(failures).isEmpty()
    }

    @Test
    fun `other page errors keep the normal failure flow`() {
        val failures = mutableListOf<Throwable>()
        val client = O5CredentialWebViewClient { failures.add(it) }

        client.onReceivedError(null, request(isMainFrame = true), error(WebViewClient.ERROR_FAILED_SSL_HANDSHAKE))

        assertThat(failures).hasSize(1)
        assertThat(failures.single()).isInstanceOf(IOException::class.java)
        assertThat(failures.single()).isNotInstanceOf(O5CertificateNetworkException::class.java)
    }

    private fun request(isMainFrame: Boolean): WebResourceRequest = mock {
        whenever(it.isForMainFrame).thenReturn(isMainFrame)
    }

    private fun error(code: Int): WebResourceError = mock {
        whenever(it.errorCode).thenReturn(code)
        whenever(it.description).thenReturn("Page load failed")
    }
}
