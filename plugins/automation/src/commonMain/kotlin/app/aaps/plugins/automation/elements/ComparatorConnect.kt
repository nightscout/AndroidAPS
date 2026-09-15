package app.aaps.plugins.automation.elements

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.core.interfaces.resources.TextResolver

class ComparatorConnect(private val rh: TextResolver) {

    enum class Compare {
        ON_CONNECT, ON_DISCONNECT;

        val stringRes: TextRef
            get() = when (this) {
                ON_CONNECT -> AutomationStrings.onconnect
                ON_DISCONNECT -> AutomationStrings.ondisconnect
            }

        companion object {

            fun labels(rh: TextResolver): List<String> {
                val list: MutableList<String> = ArrayList()
                for (c in Compare.entries) list.add(rh.gs(c.stringRes))
                return list
            }
        }
    }

    constructor(rh: TextResolver, value: Compare) : this(rh) {
        this.value = value
    }

    var value = Compare.ON_CONNECT
}
