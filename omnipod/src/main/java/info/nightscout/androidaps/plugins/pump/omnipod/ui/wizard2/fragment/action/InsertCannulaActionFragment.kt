package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment.action

import info.nightscout.androidaps.plugins.pump.omnipod.R

class InsertCannulaActionFragment : ActionFragmentBase() {
    override fun getText(): String = "[insert cannula]"

    override fun getNextPageActionId(): Int = R.id.action_insertCannulaActionFragment_to_podReplacedInfoFragment
}