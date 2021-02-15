package info.nightscout.androidaps.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.RawRes
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
        errorDialog.status = intent.getStringExtra(STATUS)
        errorDialog.sound = intent.getIntExtra(SOUND_ID, R.raw.error)
        errorDialog.title = intent.getStringExtra(TITLE)
        errorDialog.show(supportFragmentManager, "Error")

        if (sp.getBoolean(R.string.key_ns_create_announcements_from_errors, true)) {
            nsUpload.uploadError(intent.getStringExtra(STATUS))
        }
    }

    companion object {
        const val SOUND_ID = "soundId"
        const val STATUS = "status"
        const val TITLE = "title"

        fun runAlarm(ctx: Context, status: String, title : String, @RawRes soundId: Int = 0) {
            val i = Intent(ctx, ErrorHelperActivity::class.java)
            i.putExtra(SOUND_ID, soundId)
            i.putExtra(STATUS, status)
            i.putExtra(TITLE, title)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        }
    }
}
