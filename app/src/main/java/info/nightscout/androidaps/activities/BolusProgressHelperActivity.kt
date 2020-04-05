package info.nightscout.androidaps.activities

import android.os.Bundle
import info.nightscout.androidaps.dialogs.BolusProgressDialog

class BolusProgressHelperActivity : DialogAppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BolusProgressDialog()
            .setHelperActivity(this)
            .setInsulin(intent.getDoubleExtra("insulin", 0.0))
            .show(supportFragmentManager, "BolusProgress")
    }
}