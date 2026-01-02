package app.aaps.pump.omnipod.eros.ui.wizard.activation.viewmodel.info

import androidx.annotation.StringRes
import app.aaps.pump.omnipod.common.ui.wizard.activation.viewmodel.info.StartPodActivationViewModel
import app.aaps.pump.omnipod.eros.R
import javax.inject.Inject

class ErosStartPodActivationViewModel @Inject constructor() : StartPodActivationViewModel() {

    @StringRes
    override fun getTitleId(): Int = app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_activation_wizard_start_pod_activation_title

    @StringRes
    override fun getTextId(): Int = R.string.omnipod_eros_pod_activation_wizard_start_pod_activation_text
}