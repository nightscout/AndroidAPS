package app.aaps.ui.activities

import android.os.Bundle
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.database.impl.AppRepository
import app.aaps.database.impl.transactions.InsertTherapyEventAnnouncementTransaction
import app.aaps.ui.alertDialogs.ErrorDialog
import app.aaps.ui.services.AlarmSoundService
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class ErrorHelperActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var sp: SP
    @Inject lateinit var repository: AppRepository

    private val disposable = CompositeDisposable()

    @Override
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val errorDialog = ErrorDialog()
        errorDialog.helperActivity = this
        errorDialog.status = intent.getStringExtra(AlarmSoundService.STATUS) ?: ""
        errorDialog.sound = intent.getIntExtra(AlarmSoundService.SOUND_ID, app.aaps.core.ui.R.raw.error)
        errorDialog.title = intent.getStringExtra(AlarmSoundService.TITLE) ?: ""
        errorDialog.show(supportFragmentManager, "Error")

        if (sp.getBoolean(app.aaps.core.utils.R.string.key_ns_create_announcements_from_errors, true))
            disposable += repository.runTransaction(InsertTherapyEventAnnouncementTransaction(intent.getStringExtra(AlarmSoundService.STATUS) ?: "")).subscribe()
    }
}
