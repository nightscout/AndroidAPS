package app.aaps.pump.omnipod.common.ui.wizard.deactivation

import android.os.Bundle
import app.aaps.pump.omnipod.common.R
import app.aaps.pump.omnipod.common.ui.wizard.common.activity.OmnipodWizardActivityBase

abstract class PodDeactivationWizardActivity : OmnipodWizardActivityBase() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.omnipod_common_pod_deactivation_wizard_activity)

        title = getString(R.string.omnipod_common_pod_management_button_deactivate_pod)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

    }

    override fun getTotalDefinedNumberOfSteps(): Int = 3

    override fun getActualNumberOfSteps(): Int = 3

}