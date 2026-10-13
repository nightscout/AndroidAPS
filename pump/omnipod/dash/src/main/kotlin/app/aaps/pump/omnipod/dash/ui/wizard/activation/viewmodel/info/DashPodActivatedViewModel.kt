package app.aaps.pump.omnipod.dash.ui.wizard.activation.viewmodel.info

import androidx.annotation.StringRes
import app.aaps.pump.omnipod.common.ui.wizard.activation.viewmodel.info.PodActivatedViewModel
import javax.inject.Inject

class DashPodActivatedViewModel @Inject constructor() : PodActivatedViewModel() {

    @StringRes
    override fun getTitleId(): Int = app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_activation_wizard_pod_activated_title

    @StringRes
    override fun getTextId() = app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_activation_wizard_pod_activated_text
}
