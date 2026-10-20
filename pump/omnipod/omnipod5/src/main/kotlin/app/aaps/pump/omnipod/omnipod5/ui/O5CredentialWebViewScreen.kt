package app.aaps.pump.omnipod.omnipod5.ui

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.aaps.pump.omnipod.common.R
import kotlin.math.max

/**
 * Name the web page uses to reach the app bridge: the page calls
 * `bridge.postMessage(jsonString)` to hand back the Omnipod 5 certificate.
 */
private const val BRIDGE_NAME = "aapsKeymanagerBridge"

/**
 * Origins the bridge accepts messages from. "*" allows every origin, which is fine for the
 * stubbed URL; tighten this once the real certificate page URL is known.
 */
private val ALLOWED_ORIGIN_RULES = setOf("*")

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
                        webViewClient = WebViewClient()
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
                update = {
                    it.loadUrl(url)
                }
            )
            if (maxProgress <= 50) {
                Column(modifier = Modifier.align(Alignment.Center)) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Text(
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                        text = stringResource(app.aaps.core.ui.R.string.loading)
                    )
                }
            }
        }
    }
}
