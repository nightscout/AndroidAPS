package app.aaps.plugins.sync.openhumans.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalPreferences
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
            CompositionLocalProvider(
                LocalPreferences provides preferences
            ) {
                AapsTheme {
                    OHLoginScreen(
                        viewModel = viewModel,
                        authUrl = authUrl,
                        onFinishActivity = { finish() }
                    )
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
