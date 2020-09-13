package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2

import androidx.lifecycle.ViewModel
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.pump.omnipod.manager.AapsPodStateManager
import javax.inject.Inject

class WizardViewModel1 @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val podStateManager: AapsPodStateManager
) : ViewModel() {

    fun doSomethingForTesting(){
        aapsLogger.debug("This is a test that dagger initializes the viewModel.")
    }

    fun onButtonPressedForTesting(){
        aapsLogger.debug("This is a test that dagger initializes the viewModel.")
    }
}