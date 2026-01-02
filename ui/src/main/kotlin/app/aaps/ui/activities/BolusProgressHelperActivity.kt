package app.aaps.ui.activities

import android.os.Bundle
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.ui.dialogs.BolusProgressDialog

class BolusProgressHelperActivity : TranslatedDaggerAppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BolusProgressDialog()
            .setHelperActivity(this)
            .show(supportFragmentManager, "BolusProgress")
    }
}