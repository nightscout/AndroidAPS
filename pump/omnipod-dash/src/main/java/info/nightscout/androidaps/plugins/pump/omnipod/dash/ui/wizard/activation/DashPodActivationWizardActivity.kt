package info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.activation

import android.os.Bundle
import app.aaps.core.interfaces.pump.BlePreCheck
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.PodActivationWizardActivity
import javax.inject.Inject

class DashPodActivationWizardActivity : PodActivationWizardActivity() {

    @Inject lateinit var blePreCheck: BlePreCheck

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        blePreCheck.prerequisitesCheck(this)
    }
}
