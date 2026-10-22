package app.aaps.pump.omnipod.omnipod5.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.pump.omnipod.common.R
import app.aaps.core.ui.R as CoreUiR

/**
 * Full-screen content that hosts the Omnipod 5 certificate import WebView (see
 * [O5CredentialWebViewScreen]). The screen loads [url], waits for the page to post the
 * certificate JSON back through the message bridge, and imports it via [onImportCredential].
 *
 * - On a successful import [onImported] is called, which opens the Omnipod 5 setup wizard.
 * - On a failed import a "Try again" button is shown that re-launches the WebView (reloads
 *   [url] from scratch).
 * - A network failure shows a dialog explaining the one-time internet requirement.
 *
 * The host provides toolbar navigation for leaving this full-screen flow.
 */
@Composable
fun O5CredentialImportWebViewScreen(
    url: String,
    onImportCredential: (String) -> Boolean,
    onImported: () -> Unit,
    onFailed: (Throwable) -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var networkUnavailable by remember { mutableStateOf(false) }
    val retry: () -> Unit = {
        networkUnavailable = false
        failed = false
        attempt++
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)
    ) {
        if (failed) {
            Column(
                modifier = Modifier.padding(horizontal = AapsSpacing.extraLarge),
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)
            ) {
                Text(
                    text = stringResource(
                        if (networkUnavailable) R.string.omnipod_5_certificate_internet_required_message
                        else R.string.omnipod_5_certificate_import_failed
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = retry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.omnipod_5_certificate_import_try_again))
                }
            }
        } else {
            // key(attempt) recreates the WebView so "Try again" reloads the page.
            key(attempt) {
                O5CredentialWebViewScreen(
                    url = url,
                    onCredentialReceived = { json ->
                        if (onImportCredential(json)) {
                            onImported()
                        } else {
                            failed = true
                        }
                    },
                    onCredentialError = { throwable ->
                        failed = true
                        if (throwable is O5CertificateNetworkException) {
                            networkUnavailable = true
                        } else {
                            onFailed(throwable)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}
