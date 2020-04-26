package info.nightscout.androidaps.plugins.general.actions.defs

import info.nightscout.androidaps.R

class CustomAction @JvmOverloads constructor(val name: Int, val customActionType: CustomActionType?, val iconResourceId: Int = R.drawable.icon_actions_profileswitch, var isEnabled: Boolean = true) {

    constructor(nameResourceId: Int, actionType: CustomActionType?, enabled: Boolean) :
            this(nameResourceId, actionType, R.drawable.icon_actions_profileswitch, enabled)

}