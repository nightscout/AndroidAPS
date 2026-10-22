package app.aaps.pump.omnipod.omnipod5.ui

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.pump.omnipod.common.R
import java.io.IOException
import kotlin.math.max
import app.aaps.core.ui.R as CoreUiR

/**
 * Name the web page uses to reach the app bridge: the page calls
 * `bridge.postMessage(jsonString)` to hand back the Omnipod 5 certificate.
 */
private const val BRIDGE_NAME = "aapsKeymanagerBridge"

/**
 * Only the certificate service origin may send messages through the bridge.
 */
private val ALLOWED_ORIGIN_RULES = setOf("https://api.osaid-keymanager.org")

internal class O5CertificateNetworkException(message: String) : IOException(message)

internal class O5CredentialWebViewClient(
    private val onError: (Throwable) -> Unit
) : WebViewClient() {

    override fun onReceivedError(view: WebView?, request: WebResourceRequest, error: WebResourceError) {
        if (!request.isForMainFrame) return

        val message = error.description.toString()
        val failure = when (error.errorCode) {
            ERROR_HOST_LOOKUP, ERROR_CONNECT, ERROR_IO, ERROR_TIMEOUT -> O5CertificateNetworkException(message)
            else                                                   -> IOException(message)
        }
        onError(failure)
    }
}

/**
 * Full-screen WebView shown instead of the manual certificate import screen when no Omnipod 5
 * certificate is installed yet. It loads [url] (a pairing/certificate page) and listens for a
 * single message posted through `bridge.postMessage(...)` using
 * [WebViewCompat.addWebMessageListener]. The received message is expected to be the certificate
 * JSON string; it is handed back through [onCredentialReceived].
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun O5CredentialWebViewScreen(
    url: String,
    onCredentialReceived: (String) -> Unit,
    onCredentialError: (Throwable) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnCredentialReceived by rememberUpdatedState(onCredentialReceived)
    val currentOnCredentialError by rememberUpdatedState(onCredentialError)

    if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.omnipod_5_certificate_bridge_unsupported),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    var progress by remember { mutableIntStateOf(0) }
    var maxProgress by remember { mutableIntStateOf(0) }
    Column(modifier = modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0, 100) / 100f },
            modifier = Modifier.fillMaxWidth(),
            trackColor = MaterialTheme.colorScheme.surface,
        )
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                                maxProgress = max(maxProgress, newProgress)
                            }
                        }
                        webViewClient = O5CredentialWebViewClient { currentOnCredentialError(it) }
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                            WebViewCompat.addWebMessageListener(
                                this,
                                BRIDGE_NAME,
                                ALLOWED_ORIGIN_RULES
                            ) { _, message, _, _, _ ->
                                if (message.type == WebMessageCompat.TYPE_STRING) {
                                    message.data?.let { currentOnCredentialReceived(it) }
                                }
                            }
                        } else {
                            currentOnCredentialError(UnsupportedOperationException("WebView WebMessageListener feature not supported"))
                        }
                        loadUrl(url)
                    }
                },
                onRelease = { it.destroy() }
            )
            if (maxProgress <= 50) {
                Column(modifier = Modifier.align(Alignment.Center)) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Text(
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = AapsSpacing.small),
                        text = stringResource(CoreUiR.string.loading)
                    )
                }
            }
        }
    }
}
