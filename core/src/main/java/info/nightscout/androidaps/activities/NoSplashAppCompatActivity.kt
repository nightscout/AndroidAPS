package info.nightscout.androidaps.activities

import android.content.Context
import android.os.Bundle
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.utils.locale.LocaleHelper

open class NoSplashAppCompatActivity : DaggerAppCompatActivityWithResult() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.AppTheme_NoActionBar)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
}
