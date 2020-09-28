package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment.info

import info.nightscout.androidaps.plugins.pump.omnipod.R

class PodDeactivatedInfoFragment : InfoFragmentBase() {
    override fun getText(): String = "Please remove the Pod from your body"

    override fun getNextPageActionId(): Int = R.id.action_podDeactivatedInfoFragment_to_fillPodInfoFragment

    override fun getTitle(): String = "Remove Pod"

    override fun getIndex(): Int = 3
}