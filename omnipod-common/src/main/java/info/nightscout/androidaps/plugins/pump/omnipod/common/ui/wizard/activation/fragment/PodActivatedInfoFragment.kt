package info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.fragment

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import info.nightscout.androidaps.plugins.pump.omnipod.common.R
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.common.fragment.InfoFragmentBase

class PodActivatedInfoFragment : InfoFragmentBase() {

    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_common_pod_activation_wizard_pod_activated_title

    @StringRes
    override fun getTextId(): Int = R.string.omnipod_common_pod_activation_wizard_pod_activated_text

    @IdRes
    override fun getNextPageActionId(): Int? = null

    override fun getIndex(): Int = 5
}