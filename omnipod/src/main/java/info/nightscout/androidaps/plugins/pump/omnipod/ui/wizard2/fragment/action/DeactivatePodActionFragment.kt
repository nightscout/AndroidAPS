package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment.action

import info.nightscout.androidaps.plugins.pump.omnipod.R

class DeactivatePodActionFragment : ActionFragmentBase() {
    override fun getText(): String = "[deactivate Pod]"

    override fun getNextPageActionId(): Int = R.id.action_deactivatePodActionFragment_to_podDeactivatedInfoFragment
}