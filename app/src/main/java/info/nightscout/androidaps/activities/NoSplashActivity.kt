package info.nightscout.androidaps.activities

import android.app.Activity
import android.os.Bundle

import info.nightscout.androidaps.R

open class NoSplashActivity : Activity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_NoActionBar)
        super.onCreate(savedInstanceState)
    }
}
