package info.nightscout.interfaces.pump.actions

import info.nightscout.interfaces.R

class CustomAction @JvmOverloads constructor(val name: Int, val customActionType: CustomActionType, val iconResourceId: Int = R.drawable.ic_actions_profileswitch, var isEnabled: Boolean = true) {

    constructor(nameResourceId: Int, actionType: CustomActionType, enabled: Boolean) :
        this(nameResourceId, actionType, R.drawable.ic_actions_profileswitch, enabled)
}