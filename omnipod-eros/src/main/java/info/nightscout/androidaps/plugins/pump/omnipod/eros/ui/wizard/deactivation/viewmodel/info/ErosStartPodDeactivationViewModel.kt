package info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.deactivation.viewmodel.info

import androidx.annotation.StringRes
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.viewmodel.info.StartPodDeactivationViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.eros.R
import javax.inject.Inject

class ErosStartPodDeactivationViewModel @Inject constructor() : StartPodDeactivationViewModel() {

    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_common_pod_deactivation_wizard_start_pod_deactivation_title

    @StringRes
    override fun getTextId() = R.string.omnipod_common_pod_deactivation_wizard_start_pod_deactivation_text
}