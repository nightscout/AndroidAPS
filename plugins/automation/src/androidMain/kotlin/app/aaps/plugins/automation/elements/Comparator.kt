package app.aaps.plugins.automation.elements

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.plugins.automation.R

class Comparator(private val rh: ResourceHelper) {

    enum class Compare {
        IS_LESSER,
        IS_EQUAL_OR_LESSER,
        IS_EQUAL,
        IS_EQUAL_OR_GREATER,
        IS_GREATER,
        IS_NOT_AVAILABLE;

        val stringRes: TextRef
            get() = when (this) {
                IS_LESSER           -> AutomationStrings.islesser
                IS_EQUAL_OR_LESSER  -> AutomationStrings.isequalorlesser
                IS_EQUAL            -> AutomationStrings.isequal
                IS_EQUAL_OR_GREATER -> AutomationStrings.isequalorgreater
                IS_GREATER          -> AutomationStrings.isgreater
                IS_NOT_AVAILABLE    -> AutomationStrings.isnotavailable
            }

        fun <T : Comparable<T>> check(obj1: T, obj2: T): Boolean {
            val comparison = obj1.compareTo(obj2)
            return when (this) {
                IS_LESSER           -> comparison < 0
                IS_EQUAL_OR_LESSER  -> comparison <= 0
                IS_EQUAL            -> comparison == 0
                IS_EQUAL_OR_GREATER -> comparison >= 0
                IS_GREATER          -> comparison > 0
                else                -> false
            }
        }

        companion object {

            fun labels(rh: ResourceHelper): List<String> {
                val list: MutableList<String> = ArrayList()
                for (c in Compare.entries) {
                    list.add(rh.gs(c.stringRes))
                }
                return list
            }
        }
    }

    constructor(rh: ResourceHelper, value: Compare) : this(rh) {
        this.value = value
    }

    var value = Compare.IS_EQUAL

    fun setValue(compare: Compare): Comparator {
        value = compare
        return this
    }
}
