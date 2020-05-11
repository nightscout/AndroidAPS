package info.nightscout.androidaps.activities

import android.os.Bundle
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.dialogs.ErrorDialog
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject

class ErrorHelperActivity : DialogAppCompatActivity() {
    @Inject lateinit var sp : SP
    @Inject lateinit var nsUpload: NSUpload

    @Override
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val errorDialog = ErrorDialog()
        errorDialog.helperActivity = this
        errorDialog.status = intent.getStringExtra("status")
        errorDialog.sound = intent.getIntExtra("soundid", R.raw.error)
        errorDialog.title = intent.getStringExtra("title")
        errorDialog.show(supportFragmentManager, "Error")

        if (sp.getBoolean(R.string.key_ns_create_announcements_from_errors, true)) {
            nsUpload.uploadError(intent.getStringExtra("status"))
        }
    }
}
