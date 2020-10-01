package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.fragment.info

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import info.nightscout.androidaps.plugins.pump.omnipod.R

class PodDeactivatedInfoFragment : InfoFragmentBase() {
    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_change_pod_wizard_pod_deactivated_title

    @StringRes
    override fun getTextId(): Int = R.string.omnipod_change_pod_wizard_pod_deactivated_text

    @IdRes
    override fun getNextPageActionId(): Int = R.id.action_podDeactivatedInfoFragment_to_fillPodInfoFragment

    override fun getIndex(): Int = 3
}