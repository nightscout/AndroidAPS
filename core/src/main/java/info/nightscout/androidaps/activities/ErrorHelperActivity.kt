package info.nightscout.androidaps.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.RawRes
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.transactions.InsertTherapyEventAnnouncementTransaction
import info.nightscout.androidaps.dialogs.ErrorDialog
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject

class ErrorHelperActivity : DialogAppCompatActivity() {

    @Inject lateinit var sp: SP
    @Inject lateinit var repository: AppRepository

    private val disposable = CompositeDisposable()

    @Override
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val errorDialog = ErrorDialog()
        errorDialog.helperActivity = this
        errorDialog.status = intent.getStringExtra(STATUS)
        errorDialog.sound = intent.getIntExtra(SOUND_ID, R.raw.error)
        errorDialog.title = intent.getStringExtra(TITLE)
        errorDialog.show(supportFragmentManager, "Error")

        if (sp.getBoolean(R.string.key_ns_create_announcements_from_errors, true))
            disposable += repository.runTransaction(InsertTherapyEventAnnouncementTransaction(intent.getStringExtra(STATUS))).subscribe()
    }

    companion object {

        const val SOUND_ID = "soundId"
        const val STATUS = "status"
        const val TITLE = "title"

        fun runAlarm(ctx: Context, status: String, title: String, @RawRes soundId: Int = 0) {
            val i = Intent(ctx, ErrorHelperActivity::class.java)
            i.putExtra(SOUND_ID, soundId)
            i.putExtra(STATUS, status)
            i.putExtra(TITLE, title)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        }
    }
}
