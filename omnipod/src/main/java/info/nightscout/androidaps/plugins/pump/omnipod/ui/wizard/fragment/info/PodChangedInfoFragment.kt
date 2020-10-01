package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.fragment.info

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import info.nightscout.androidaps.plugins.pump.omnipod.R

class PodChangedInfoFragment : InfoFragmentBase() {
    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_change_pod_wizard_pod_changed_title

    @StringRes
    override fun getTextId(): Int = R.string.omnipod_change_pod_wizard_pod_changed_text

    @IdRes
    override fun getNextPageActionId(): Int? = null

    override fun getIndex(): Int = 8
}