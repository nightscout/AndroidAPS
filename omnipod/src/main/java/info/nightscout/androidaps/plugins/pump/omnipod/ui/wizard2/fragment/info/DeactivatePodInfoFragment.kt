package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment.info

import info.nightscout.androidaps.plugins.pump.omnipod.R

class DeactivatePodInfoFragment : InfoFragmentBase() {
    override fun getText(): String = "Deactivate the Pod"

    override fun getNextPageActionId(): Int = R.id.action_deactivatePodInfoFragment_to_deactivatePodActionFragment

    override fun getTitle(): String = "Deactivate Pod"

    override fun getIndex(): Int = 1
}