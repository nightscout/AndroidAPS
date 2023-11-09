package app.aaps.wear.interaction.actions

import android.os.Bundle
import android.widget.Toast
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.comm.DataLayerListenerServiceWear
import dagger.android.DaggerActivity
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
