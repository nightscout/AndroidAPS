package info.nightscout.ui.activities

import android.os.Bundle
import info.nightscout.core.ui.activities.DialogAppCompatActivity
import info.nightscout.ui.dialogs.BolusProgressDialog

class BolusProgressHelperActivity : DialogAppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BolusProgressDialog()
            .setHelperActivity(this)
            .setInsulin(intent.getDoubleExtra("insulin", 0.0))
            .setId(intent.getLongExtra("id", 0L))
            .show(supportFragmentManager, "BolusProgress")
    }
}