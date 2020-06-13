package info.nightscout.androidaps.activities

import android.content.res.Resources
import android.os.Bundle
import info.nightscout.androidaps.dialogs.BolusProgressDialog
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject

class BolusProgressHelperActivity : DialogAppCompatActivity() {
    @Inject lateinit var sp: SP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        BolusProgressDialog()
            .setHelperActivity(this)
            .setInsulin(intent.getDoubleExtra("insulin", 0.0))
            .show(supportFragmentManager, "BolusProgress")
    }
}