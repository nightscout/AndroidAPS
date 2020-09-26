package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment.info

import info.nightscout.androidaps.plugins.pump.omnipod.R

class AttachPodInfoFragment : InfoFragmentBase() {
    override fun getText(): String = "Attach the Pod"

    override fun getNextPageActionId(): Int = R.id.action_attachPodInfoFragment_to_insertCannulaActionFragment
}