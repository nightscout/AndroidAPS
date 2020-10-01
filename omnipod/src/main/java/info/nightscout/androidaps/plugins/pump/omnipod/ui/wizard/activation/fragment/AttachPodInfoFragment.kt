package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.activation.fragment

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import info.nightscout.androidaps.plugins.pump.omnipod.R
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.common.fragment.InfoFragmentBase

class AttachPodInfoFragment : InfoFragmentBase() {
    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_pod_activation_wizard_attach_pod_title

    @StringRes
    override fun getTextId(): Int = R.string.omnipod_pod_activation_wizard_attach_pod_text

    @IdRes
    override fun getNextPageActionId(): Int = R.id.action_attachPodInfoFragment_to_insertCannulaActionFragment

    override fun getIndex(): Int = 3
}