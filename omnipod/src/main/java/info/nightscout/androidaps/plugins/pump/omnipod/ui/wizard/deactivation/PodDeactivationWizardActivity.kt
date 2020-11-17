package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.deactivation

import android.os.Bundle
import info.nightscout.androidaps.plugins.pump.omnipod.R
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.common.activity.OmnipodWizardActivityBase

class PodDeactivationWizardActivity : OmnipodWizardActivityBase() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.omnipod_pod_deactivation_wizard_activity)
    }

    override fun getTotalDefinedNumberOfSteps(): Int = 3

    override fun getActualNumberOfSteps(): Int = 3

}