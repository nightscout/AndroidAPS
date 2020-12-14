package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.deactivation.fragment

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import info.nightscout.androidaps.plugins.pump.omnipod.R
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.common.fragment.InfoFragmentBase

class PodDiscardedInfoFragment : InfoFragmentBase() {

    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_pod_deactivation_wizard_pod_discarded_title

    @StringRes
    override fun getTextId(): Int = R.string.omnipod_pod_deactivation_wizard_pod_discarded_text

    @IdRes
    override fun getNextPageActionId(): Int? = null

    override fun getIndex(): Int = 3
}