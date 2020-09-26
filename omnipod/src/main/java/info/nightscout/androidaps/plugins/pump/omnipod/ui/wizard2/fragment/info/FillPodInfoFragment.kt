package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment.info

import info.nightscout.androidaps.plugins.pump.omnipod.R

class FillPodInfoFragment : InfoFragmentBase() {
    override fun getText(): String = "Fill the Pod"

    override fun getNextPageActionId(): Int = R.id.action_fillPodInfoFragment_to_pairAndPrimePodActionFragment
}