package app.aaps.plugins.automation.elements

import androidx.annotation.StringRes
import app.aaps.core.interfaces.resources.ResourceHelper

class ComparatorExists(private val rh: ResourceHelper, var value: Compare = Compare.EXISTS) {

    enum class Compare {
        EXISTS, NOT_EXISTS;

        @get:StringRes val stringRes: Int
            get() = when (this) {
                EXISTS -> app.aaps.core.ui.R.string.exists
                NOT_EXISTS -> app.aaps.core.ui.R.string.notexists
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
