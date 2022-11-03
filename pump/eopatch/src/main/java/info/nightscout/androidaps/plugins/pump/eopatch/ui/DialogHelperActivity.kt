package info.nightscout.androidaps.plugins.pump.eopatch.ui

import android.os.Bundle
import info.nightscout.androidaps.activities.DialogAppCompatActivity
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.plugins.pump.eopatch.ui.dialogs.ActivationNotCompleteDialog

class DialogHelperActivity : DialogAppCompatActivity() {
    @Override
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.AppTheme_NoActionBar)

        val dialog = ActivationNotCompleteDialog()
        dialog.helperActivity = this

        dialog.title = intent.getStringExtra("title")?:""
        dialog.message = intent.getStringExtra("message")?:""
        dialog.show(supportFragmentManager, "Dialog")
    }
}