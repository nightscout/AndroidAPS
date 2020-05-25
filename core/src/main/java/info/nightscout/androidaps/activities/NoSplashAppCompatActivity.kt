package info.nightscout.androidaps.activities

import android.content.Context
import android.os.Bundle
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.utils.locale.LocaleHelper

//@Suppress("registered")
open class NoSplashAppCompatActivity : DaggerAppCompatActivity() {

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.AppTheme_NoActionBar)
    }

    public override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
}
