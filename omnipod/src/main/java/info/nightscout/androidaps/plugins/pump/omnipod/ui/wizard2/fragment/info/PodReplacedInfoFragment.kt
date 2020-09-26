package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment.info

import info.nightscout.androidaps.plugins.pump.omnipod.R

class PodReplacedInfoFragment : InfoFragmentBase() {
    override fun getText(): String = "the Pod has been replaced"

    override fun getNextPageActionId(): Int? = null
}