package app.aaps.plugins.automation.elements

import androidx.annotation.StringRes
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.plugins.automation.R

class InputLocationMode(private val rh: ResourceHelper) {

    enum class Mode {
        INSIDE, OUTSIDE, GOING_IN, GOING_OUT;

        @get:StringRes val stringRes: Int
            get() = when (this) {
                INSIDE    -> R.string.location_inside
                OUTSIDE   -> R.string.location_outside
                GOING_IN  -> R.string.location_going_in
                GOING_OUT -> R.string.location_going_out
            }

        companion object {

            fun labels(rh: ResourceHelper): List<String> {
                val list: MutableList<String> = ArrayList()
                for (c in Mode.entries) {
                    list.add(rh.gs(c.stringRes))
                }
                return list
            }
        }
    }

    var value: Mode = Mode.INSIDE

    constructor(rh: ResourceHelper, value: Mode) : this(rh) {
        this.value = value
    }
}
