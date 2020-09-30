package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.fragment.info

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import info.nightscout.androidaps.plugins.pump.omnipod.R

class PodDiscardedInfoFragment : InfoFragmentBase() {
    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_change_pod_wizard_pod_discarded_title

    @StringRes
    override fun getTextId(): Int = R.string.omnipod_change_pod_wizard_pod_discarded_text

    @IdRes
    override fun getNextPageActionId(): Int = R.id.action_podDiscardedInfoFragment_to_fillPodInfoFragment

    override fun getIndex(): Int = 3
}