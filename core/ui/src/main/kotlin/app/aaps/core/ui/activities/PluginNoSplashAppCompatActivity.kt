package app.aaps.core.ui.activities

import android.os.Bundle
import dagger.android.support.DaggerAppCompatActivity
import app.aaps.core.ui.R

open class PluginNoSplashAppCompatActivity : DaggerAppCompatActivity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.AppTheme)
    }
}