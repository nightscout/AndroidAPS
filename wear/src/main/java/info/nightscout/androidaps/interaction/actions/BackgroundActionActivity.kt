package info.nightscout.androidaps.interaction.actions

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import info.nightscout.androidaps.data.ListenerService

const val TAG = "QuickWizard"

class BackgroundActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val actionString = intent.extras?.getString("actionString")
        Log.i(TAG, "QuickWizardActivity.onCreate: actionString=$actionString")
        if (actionString != null) {
            ListenerService.initiateAction(this, actionString)
            val message = intent.extras?.getString("message")
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        } else {
            Log.e(TAG, "BackgroundActionActivity.onCreate extras 'actionString' required")
        }
        finishAffinity()
    }

}
