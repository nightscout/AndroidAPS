package info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.activation.viewmodel.info

import androidx.annotation.StringRes
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.info.AttachPodViewModel
import javax.inject.Inject

class DashAttachPodViewModel @Inject constructor() : AttachPodViewModel() {

    @StringRes
    override fun getTitleId(): Int = info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_pod_activation_wizard_attach_pod_title

    @StringRes
    override fun getTextId() = info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_pod_activation_wizard_attach_pod_text
}
