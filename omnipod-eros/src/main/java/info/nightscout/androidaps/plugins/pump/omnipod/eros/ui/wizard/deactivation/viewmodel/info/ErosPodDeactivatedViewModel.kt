package info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.deactivation.viewmodel.info

import androidx.annotation.StringRes
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.viewmodel.info.PodDeactivatedViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.eros.R
import javax.inject.Inject

class ErosPodDeactivatedViewModel @Inject constructor() : PodDeactivatedViewModel() {

    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_common_pod_deactivation_wizard_pod_deactivated_title

    @StringRes
    override fun getTextId() = R.string.omnipod_common_pod_deactivation_wizard_pod_deactivated_text

}