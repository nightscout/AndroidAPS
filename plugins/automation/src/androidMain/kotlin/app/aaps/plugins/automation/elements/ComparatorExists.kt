package app.aaps.plugins.automation.elements

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.interfaces.resources.ResourceHelper

class ComparatorExists(private val rh: ResourceHelper, var value: Compare = Compare.EXISTS) {

    enum class Compare {
        EXISTS, NOT_EXISTS;

        val stringRes: TextRef
            get() = when (this) {
                EXISTS -> CoreUiStrings.exists
                NOT_EXISTS -> CoreUiStrings.notexists
            }

        companion object {

            fun labels(rh: ResourceHelper): List<String> {
                val list: MutableList<String> = ArrayList()
                for (c in Compare.entries) list.add(rh.gs(c.stringRes))
                return list
            }
        }
    }
}
