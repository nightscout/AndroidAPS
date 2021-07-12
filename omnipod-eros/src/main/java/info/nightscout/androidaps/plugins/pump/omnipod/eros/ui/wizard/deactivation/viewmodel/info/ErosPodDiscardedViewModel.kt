package info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.deactivation.viewmodel.info

import androidx.annotation.StringRes
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.viewmodel.info.PodDiscardedViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.eros.R
import javax.inject.Inject

class ErosPodDiscardedViewModel @Inject constructor() : PodDiscardedViewModel() {

    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_common_pod_deactivation_wizard_pod_discarded_title

    @StringRes
    override fun getTextId() = R.string.omnipod_common_pod_deactivation_wizard_pod_discarded_text
}