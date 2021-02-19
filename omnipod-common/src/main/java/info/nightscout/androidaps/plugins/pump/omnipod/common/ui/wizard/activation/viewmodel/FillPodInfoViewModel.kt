package info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.common.R

class FillPodInfoViewModel : ViewModel() {

    @StringRes fun getTextId(): Int = R.string.omnipod_pod_activation_wizard_fill_pod_text
}