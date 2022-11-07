package info.nightscout.androidaps.interaction.actions

import android.os.Bundle
import android.widget.Toast
import dagger.android.DaggerActivity
import info.nightscout.androidaps.comm.DataLayerListenerServiceWear
import info.nightscout.rx.events.EventWearToMobile
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag

import info.nightscout.rx.weardata.EventData
import javax.inject.Inject

class BackgroundActionActivity : DaggerActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.extras?.getString(DataLayerListenerServiceWear.KEY_ACTION)?.let { action ->
            aapsLogger.info(LTag.WEAR, "QuickWizardActivity.onCreate: action=$action")
            rxBus.send(EventWearToMobile(EventData.deserialize(action)))
            intent.extras?.getString(DataLayerListenerServiceWear.KEY_MESSAGE)?.let { message ->
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        } ?: aapsLogger.error(LTag.WEAR, "BackgroundActionActivity.onCreate extras 'actionString' required")
        finishAffinity()
    }

}
