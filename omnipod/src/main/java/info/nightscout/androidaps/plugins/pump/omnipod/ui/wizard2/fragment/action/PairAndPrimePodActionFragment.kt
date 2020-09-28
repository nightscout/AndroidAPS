package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment.action

import info.nightscout.androidaps.plugins.pump.omnipod.R

class PairAndPrimePodActionFragment : ActionFragmentBase() {
    override fun getText(): String = "[pair and prime Pod]"

    override fun getNextPageActionId(): Int = R.id.action_pairAndPrimePodActionFragment_to_attachPodInfoFragment

    override fun getTitle(): String = "Pair and prime Pod"

    override fun getIndex(): Int = 5
}