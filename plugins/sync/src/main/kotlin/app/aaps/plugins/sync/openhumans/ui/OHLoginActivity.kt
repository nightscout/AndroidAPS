package app.aaps.plugins.sync.openhumans.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.dialogs.GlobalSnackbarHost
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.plugins.sync.di.AuthUrl
import app.aaps.plugins.sync.openhumans.compose.OHLoginScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class OHLoginActivity : AppCompatActivity() {

    @Inject
    @AuthUrl
    internal lateinit var authUrl: String

    @Inject
    lateinit var preferences: Preferences

    @Inject
    lateinit var rxBus: RxBus

    private val viewModel by viewModels<OHLoginViewModel>()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val code = intent.data?.getQueryParameter("code")
        if (code != null) {
            viewModel.submitBearerToken(code)
        }

        setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            CompositionLocalProvider(
                LocalPreferences provides preferences,
                LocalSnackbarHostState provides snackbarHostState
            ) {
                AapsTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        OHLoginScreen(
                            viewModel = viewModel,
                            authUrl = authUrl,
                            onFinishActivity = { finish() }
                        )
                        GlobalSnackbarHost(
                            rxBus = rxBus,
                            hostState = snackbarHostState,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val code = intent.data?.getQueryParameter("code")
        if (code != null) {
            viewModel.submitBearerToken(code)
        }
    }
}
