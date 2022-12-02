package info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.activation.viewmodel.info

import androidx.annotation.StringRes
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.info.StartPodActivationViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.dash.R
import javax.inject.Inject

class DashStartPodActivationViewModel @Inject constructor() : StartPodActivationViewModel() {

    @StringRes
    override fun getTitleId(): Int = info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_pod_activation_wizard_start_pod_activation_title

    @StringRes
    override fun getTextId(): Int = R.string.omnipod_dash_pod_activation_wizard_start_pod_activation_text
}
