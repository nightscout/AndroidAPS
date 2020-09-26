package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment.info

import info.nightscout.androidaps.plugins.pump.omnipod.R

class PodDeactivatedInfoFragment : InfoFragmentBase() {
    override fun getText(): String = "Pod has been deactivated"

    override fun getNextPageActionId(): Int = R.id.action_podDeactivatedInfoFragment_to_fillPodInfoFragment
}