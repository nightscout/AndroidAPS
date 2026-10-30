package app.aaps.settingsstore

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.res.stringResource
import app.aaps.ComposeMainActivity
import app.aaps.R
import app.aaps.appshell.StartupFailedLabels
import app.aaps.appshell.StartupFailedScreen
import app.aaps.plugins.configuration.configBuilder.AndroidAppExit
import app.aaps.shared.impl.sharedPreferences.StoreUnreadableException
import app.aaps.core.ui.R as CoreUiR

/**
 * Shown instead of the app when the settings cannot be opened (see [AndroidStorage]): what happened, and the
 * two ways out - try again, or start fresh.
 *
 * Not injected, and not part of the app's graph, on purpose. AAPS is not started in this case, and this
 * screen needs nothing of it. `ComposeMainActivity` hands over to it at once, and the notification opens it.
 */
class SettingsUnreadableActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val failure = AndroidStorage.failure
        if (failure == null) {
            // The settings are open: the notification was older than the start that opened them.
            startActivity(Intent(this, ComposeMainActivity::class.java))
            finish()
            return
        }
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                val cause = stringResource(
                    if (failure is StoreUnreadableException) R.string.settings_unreadable_key_explanation
                    else R.string.settings_unreadable_keystore_explanation
                )
                StartupFailedScreen(
                    title = stringResource(R.string.settings_unreadable_title),
                    explanation = listOf(cause, stringResource(R.string.settings_unreadable_not_running), stringResource(R.string.settings_unreadable_what_to_do)),
                    error = failure,
                    onRetry = ::restart,
                    onStartFresh = {
                        // Said on the screen, not a crash: the file could not be set aside, so a restart would
                        // only find it again.
                        try {
                            AndroidStorage.startFresh(this)
                            restart()
                        } catch (e: Exception) {
                            Toast.makeText(this, e.message ?: e::class.simpleName, Toast.LENGTH_LONG).show()
                        }
                    },
                    labels = StartupFailedLabels(
                        tryAgain = stringResource(CoreUiR.string.retry),
                        startFresh = stringResource(R.string.settings_unreadable_start_fresh),
                        confirmStartFreshTitle = stringResource(R.string.settings_unreadable_confirm_title),
                        confirmStartFreshText = stringResource(R.string.settings_unreadable_confirm_text),
                        cancel = stringResource(CoreUiR.string.cancel)
                    )
                )
            }
        }
    }

    /** A new process, which opens the store again. The only way: the store of this one is the empty one in memory. */
    private fun restart() = AndroidAppExit(applicationContext).exit(launchAgain = true)
}
