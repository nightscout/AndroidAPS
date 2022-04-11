package info.nightscout.androidaps.interaction.actions

import android.os.Bundle
import android.widget.Toast
import dagger.android.DaggerActivity
import info.nightscout.androidaps.data.DataLayerListenerService
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import javax.inject.Inject

class BackgroundActionActivity : DaggerActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.extras?.getString("actionString")?.let { actionString ->
            aapsLogger.info(LTag.WEAR, "QuickWizardActivity.onCreate: actionString=$actionString")
            DataLayerListenerService.initiateAction(this, actionString)
            intent.extras?.getString("message")?.let { message ->
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        } ?: aapsLogger.error(LTag.WEAR, "BackgroundActionActivity.onCreate extras 'actionString' required")
        finishAffinity()
    }

}
