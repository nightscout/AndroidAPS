package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2

import android.os.Bundle
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.plugins.pump.omnipod.R

class WizardActivity : NoSplashAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.omnipod_wizard_activity) // TODO: replace with DataBindingUtil.setContentView
    }
}