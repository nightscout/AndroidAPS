package info.nightscout.androidaps.plugins.pump.danaRS.activities

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.plugins.pump.danaRS.dialogs.PairingProgressDialog

class PairingHelperActivity : NoSplashAppCompatActivity() {
    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PairingProgressDialog()
            .setHelperActivity(this)
            .show(supportFragmentManager, "PairingProgress")
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    }
}