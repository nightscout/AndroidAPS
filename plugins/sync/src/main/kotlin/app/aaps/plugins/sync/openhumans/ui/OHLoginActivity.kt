package app.aaps.plugins.sync.openhumans.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.di.AuthUrl
import app.aaps.plugins.sync.di.ViewModelFactory
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import javax.inject.Inject

class OHLoginActivity : TranslatedDaggerAppCompatActivity() {

    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory

    @Inject
    @AuthUrl
    internal lateinit var authUrl: String

    private val viewModel by viewModels<OHLoginViewModel> { viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_open_humans_login_new)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            view.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
            insets
        }

        val accept = findViewById<CheckBox>(R.id.accept)
        val login = findViewById<MaterialButton>(R.id.login)
        accept.setOnCheckedChangeListener { _, value -> login.isEnabled = value }

        login.setOnClickListener {
            CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(authUrl))
        }

        val cancel = findViewById<MaterialButton>(R.id.cancel)
        cancel.setOnClickListener { viewModel.cancel() }

        val proceed = findViewById<MaterialButton>(R.id.proceed)
        proceed.setOnClickListener { viewModel.finish() }

        val close = findViewById<MaterialButton>(R.id.close)
        close.setOnClickListener { finish() }

        val welcome = findViewById<NestedScrollView>(R.id.welcome)
        val consent = findViewById<NestedScrollView>(R.id.consent)
        val confirm = findViewById<NestedScrollView>(R.id.confirm)
        val finishing = findViewById<NestedScrollView>(R.id.finishing)
        val done = findViewById<NestedScrollView>(R.id.done)

        val welcomeNext = findViewById<MaterialButton>(R.id.welcome_next)
        welcomeNext.setOnClickListener {
            viewModel.goToConsent()
        }

        viewModel.state.observe(this) { state ->
            welcome.visibility =
                if (state == OHLoginViewModel.State.WELCOME) View.VISIBLE else View.GONE
            consent.visibility =
                if (state == OHLoginViewModel.State.CONSENT) View.VISIBLE else View.GONE
            confirm.visibility =
                if (state == OHLoginViewModel.State.CONFIRM) View.VISIBLE else View.GONE
            finishing.visibility =
                if (state == OHLoginViewModel.State.FINISHING) View.VISIBLE else View.GONE
            done.visibility =
                if (state == OHLoginViewModel.State.DONE) View.VISIBLE else View.GONE
        }

        val code = intent.data?.getQueryParameter("code")
        if (code != null) {
            viewModel.submitBearerToken(code)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!viewModel.goBack()) finish()
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val code = intent.data?.getQueryParameter("code")
        if (code != null) {
            viewModel.submitBearerToken(code)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        findViewById<CheckBox>(R.id.accept).setOnCheckedChangeListener(null)
        findViewById<MaterialButton>(R.id.login).setOnClickListener(null)
        findViewById<MaterialButton>(R.id.cancel).setOnClickListener(null)
        findViewById<MaterialButton>(R.id.proceed).setOnClickListener(null)
        findViewById<MaterialButton>(R.id.close).setOnClickListener(null)
        findViewById<MaterialButton>(R.id.welcome_next).setOnClickListener(null)
    }
}