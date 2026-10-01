package app.aaps.plugins.automation.elements

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.core.interfaces.resources.TextResolver

class InputLocationMode(private val rh: TextResolver) {

    enum class Mode {
        INSIDE, OUTSIDE, GOING_IN, GOING_OUT;

        val stringRes: TextRef
            get() = when (this) {
                INSIDE    -> AutomationStrings.location_inside
                OUTSIDE   -> AutomationStrings.location_outside
                GOING_IN  -> AutomationStrings.location_going_in
                GOING_OUT -> AutomationStrings.location_going_out
            }

        companion object {

            fun labels(rh: TextResolver): List<String> {
                val list: MutableList<String> = ArrayList()
                for (c in Mode.entries) {
                    list.add(rh.gs(c.stringRes))
                }
                return list
            }
        }
    }

    var value: Mode = Mode.INSIDE

    constructor(rh: TextResolver, value: Mode) : this(rh) {
        this.value = value
    }
}
