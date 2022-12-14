package info.nightscout.ui.activities

import android.os.Bundle
import info.nightscout.ui.services.AlarmSoundService
import info.nightscout.core.ui.activities.DialogAppCompatActivity
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.InsertTherapyEventAnnouncementTransaction
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.ui.alertDialogs.ErrorDialog
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
        errorDialog.sound = intent.getIntExtra(AlarmSoundService.SOUND_ID, info.nightscout.core.ui.R.raw.error)
        errorDialog.title = intent.getStringExtra(AlarmSoundService.TITLE) ?: ""
        errorDialog.show(supportFragmentManager, "Error")

        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_create_announcements_from_errors, true))
            disposable += repository.runTransaction(InsertTherapyEventAnnouncementTransaction(intent.getStringExtra(AlarmSoundService.STATUS) ?: "")).subscribe()
    }
}
