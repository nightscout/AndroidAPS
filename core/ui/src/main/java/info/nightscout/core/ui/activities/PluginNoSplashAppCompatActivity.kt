package info.nightscout.core.ui.activities

import android.os.Bundle
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.core.ui.R

open class PluginNoSplashAppCompatActivity : DaggerAppCompatActivity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.AppTheme)
    }
}