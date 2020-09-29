package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.fragment.info

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import info.nightscout.androidaps.plugins.pump.omnipod.R

class AttachPodInfoFragment : InfoFragmentBase() {
    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_change_pod_wizard_attach_pod_title

    @StringRes
    override fun getTextId(): Int = R.string.omnipod_change_pod_wizard_attach_pod_text

    @IdRes
    override fun getNextPageActionId(): Int = R.id.action_attachPodInfoFragment_to_insertCannulaActionFragment

    override fun getIndex(): Int = 6
}