package app.aaps.pump.eopatch.ui

import android.os.Bundle
import app.aaps.core.ui.R
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.pump.eopatch.ui.dialogs.ActivationNotCompleteDialog

class DialogHelperActivity : TranslatedDaggerAppCompatActivity() {

    @Override
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.AppTheme_NoActionBar)

        val dialog = ActivationNotCompleteDialog()
        dialog.helperActivity = this

        dialog.title = intent.getStringExtra("title") ?: ""
        dialog.message = intent.getStringExtra("message") ?: ""
        dialog.show(supportFragmentManager, "Dialog")
    }
}