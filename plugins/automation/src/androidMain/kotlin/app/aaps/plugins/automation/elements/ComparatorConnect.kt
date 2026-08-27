package app.aaps.plugins.automation.elements

import androidx.annotation.StringRes
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.plugins.automation.R

class ComparatorConnect(private val rh: ResourceHelper) {

    enum class Compare {
        ON_CONNECT, ON_DISCONNECT;

        @get:StringRes val stringRes: Int
            get() = when (this) {
                ON_CONNECT -> R.string.onconnect
                ON_DISCONNECT -> R.string.ondisconnect
            }

        companion object {

            fun labels(rh: ResourceHelper): List<String> {
                val list: MutableList<String> = ArrayList()
                for (c in Compare.entries) list.add(rh.gs(c.stringRes))
                return list
            }
        }
    }

    constructor(rh: ResourceHelper, value: Compare) : this(rh) {
        this.value = value
    }

    var value = Compare.ON_CONNECT
}
