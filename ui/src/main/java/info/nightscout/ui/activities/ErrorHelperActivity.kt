package info.nightscout.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.RawRes
import info.nightscout.androidaps.activities.DialogAppCompatActivity
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.transactions.InsertTherapyEventAnnouncementTransaction
import info.nightscout.androidaps.dialogs.ErrorDialog
import info.nightscout.androidaps.services.AlarmSoundService
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
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
        errorDialog.status = intent.getStringExtra(AlarmSoundService.STATUS) ?: ""
        errorDialog.sound = intent.getIntExtra(AlarmSoundService.SOUND_ID, R.raw.error)
        errorDialog.title = intent.getStringExtra(AlarmSoundService.TITLE) ?: ""
        errorDialog.show(supportFragmentManager, "Error")

        if (sp.getBoolean(R.string.key_ns_create_announcements_from_errors, true))
            disposable += repository.runTransaction(InsertTherapyEventAnnouncementTransaction(intent.getStringExtra(AlarmSoundService.STATUS) ?: "")).subscribe()
    }
}
