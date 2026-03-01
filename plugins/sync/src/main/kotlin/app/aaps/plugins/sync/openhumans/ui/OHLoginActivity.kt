package app.aaps.plugins.sync.openhumans.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalRxBus
import app.aaps.plugins.sync.di.AuthUrl
import app.aaps.core.ui.compose.ViewModelFactory
import app.aaps.plugins.sync.openhumans.compose.OHLoginScreen
import javax.inject.Inject

class OHLoginActivity : TranslatedDaggerAppCompatActivity() {

    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory

    @Inject
    @AuthUrl
    internal lateinit var authUrl: String

    @Inject
    lateinit var preferences: Preferences

    @Inject
    lateinit var rxBus: RxBus

    private val viewModel by viewModels<OHLoginViewModel> { viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val code = intent.data?.getQueryParameter("code")
        if (code != null) {
            viewModel.submitBearerToken(code)
        }

        setContent {
            CompositionLocalProvider(
                LocalPreferences provides preferences,
                LocalRxBus provides rxBus
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
