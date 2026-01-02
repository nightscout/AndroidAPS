package app.aaps.ui.activities

import android.os.Bundle
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.asAnnouncement
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.ui.alertDialogs.ErrorDialog
import app.aaps.ui.services.AlarmSoundService
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class ErrorHelperActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var config: Config

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

        if (preferences.get(BooleanKey.NsClientCreateAnnouncementsFromErrors) && config.APS)
            disposable += persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                therapyEvent = TE.asAnnouncement(intent.getStringExtra(AlarmSoundService.STATUS) ?: ""),
                timestamp = dateUtil.now(),
                action = Action.CAREPORTAL,
                source = Sources.Aaps,
                note = intent.getStringExtra(AlarmSoundService.STATUS) ?: "",
                listValues = listOf(ValueWithUnit.TEType(TE.Type.ANNOUNCEMENT))
            ).subscribe()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
    }
}
