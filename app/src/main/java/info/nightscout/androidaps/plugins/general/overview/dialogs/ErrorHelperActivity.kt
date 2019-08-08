package info.nightscout.androidaps.plugins.general.overview.dialogs

import android.os.Bundle
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.utils.SP

class ErrorHelperActivity : NoSplashAppCompatActivity() {

    @Override
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val errorDialog = ErrorDialog()
        errorDialog.helperActivity = this
        errorDialog.status = intent.getStringExtra("status")
        errorDialog.sound = intent.getIntExtra("soundid", R.raw.error)
        errorDialog.title = intent.getStringExtra("title")
        errorDialog.show(supportFragmentManager, "Error")

        if (SP.getBoolean(R.string.key_ns_create_announcements_from_errors, true)) {
            NSUpload.uploadError(intent.getStringExtra("status"))
        }
    }
}
