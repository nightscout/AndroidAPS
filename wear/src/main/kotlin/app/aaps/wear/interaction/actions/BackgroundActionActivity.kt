package app.aaps.wear.interaction.actions

import android.content.Intent
import android.os.Bundle
import androidx.wear.activity.ConfirmationActivity
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.R
import app.aaps.wear.comm.DataLayerListenerServiceWear
import dagger.android.DaggerActivity
import javax.inject.Inject

class BackgroundActionActivity : DaggerActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var sp: SP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.extras?.getString(DataLayerListenerServiceWear.KEY_ACTION)?.let { action ->
            aapsLogger.info(LTag.WEAR, "QuickWizardActivity.onCreate: action=$action")
            val event = EventData.deserialize(action)
            val fixedEvent = if (event is EventData.RunningModeSelected) {
                // Re-read the latest timestamp from SP so a stale tile-cached action does not get
                // rejected by the phone when a new RunningModeList has been issued since the tile rendered.
                val latestTS = runCatching {
                    (EventData.deserialize(sp.getString(R.string.key_running_mode_data, "")) as? EventData.RunningModeList)?.timeStamp
                }.getOrNull() ?: event.timeStamp
                event.copy(timeStamp = latestTS)
            } else event
            rxBus.send(EventWearToMobile(fixedEvent))
            intent.extras?.getString(DataLayerListenerServiceWear.KEY_MESSAGE)?.let { message ->
                startActivity(
                    Intent(this, ConfirmationActivity::class.java).apply {
                        putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.SUCCESS_ANIMATION)
                        putExtra(ConfirmationActivity.EXTRA_MESSAGE, message)
                    }
                )
            }
        } ?: aapsLogger.error(LTag.WEAR, "BackgroundActionActivity.onCreate extras 'actionString' required")
        finishAffinity()
    }

}
