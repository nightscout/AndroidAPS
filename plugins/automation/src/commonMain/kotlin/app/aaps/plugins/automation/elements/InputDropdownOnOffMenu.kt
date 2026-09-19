package app.aaps.plugins.automation.elements

import app.aaps.plugins.automation.AutomationStrings
import app.aaps.core.interfaces.resources.TextResolver

class InputDropdownOnOffMenu(private val rh: TextResolver) {

    var value: Boolean = true

    constructor(rh: TextResolver, state: Boolean) : this(rh) {
        value = state
    }

    @Suppress("unused")
    constructor(rh: TextResolver, another: InputDropdownOnOffMenu) : this(rh) {
        value = another.value
    }

    fun toTextValue() = when (value) {
        true  -> rh.gs(AutomationStrings.on)
        false -> rh.gs(AutomationStrings.off)
    }

    fun setValue(state: Boolean): InputDropdownOnOffMenu {
        value = state
        return this
    }
}
