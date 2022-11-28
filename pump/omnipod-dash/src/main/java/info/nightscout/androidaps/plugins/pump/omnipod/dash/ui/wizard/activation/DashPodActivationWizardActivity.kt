package info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.activation

import android.os.Bundle
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.PodActivationWizardActivity
import info.nightscout.interfaces.pump.BlePreCheck
import javax.inject.Inject

class DashPodActivationWizardActivity : PodActivationWizardActivity() {

    @Inject lateinit var blePreCheck: BlePreCheck

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        blePreCheck.prerequisitesCheck(this)
    }
}
