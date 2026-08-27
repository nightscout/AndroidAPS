package app.aaps.plugins.automation.elements

import app.aaps.core.interfaces.resources.ResourceHelper

class InputDropdownOnOffMenu(private val rh: ResourceHelper) {

    var value: Boolean = true

    constructor(rh: ResourceHelper, state: Boolean) : this(rh) {
        value = state
    }

    @Suppress("unused")
    constructor(rh: ResourceHelper, another: InputDropdownOnOffMenu) : this(rh) {
        value = another.value
    }

    fun toTextValue() = when (value) {
        true  -> rh.gs(app.aaps.plugins.automation.R.string.on)
        false -> rh.gs(app.aaps.plugins.automation.R.string.off)
    }

    fun setValue(state: Boolean): InputDropdownOnOffMenu {
        value = state
        return this
    }
}
