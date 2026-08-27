package app.aaps.plugins.automation.elements

import androidx.annotation.StringRes
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

        @get:StringRes val stringRes: Int
            get() = when (this) {
                IS_LESSER           -> R.string.islesser
                IS_EQUAL_OR_LESSER  -> R.string.isequalorlesser
                IS_EQUAL            -> R.string.isequal
                IS_EQUAL_OR_GREATER -> R.string.isequalorgreater
                IS_GREATER          -> R.string.isgreater
                IS_NOT_AVAILABLE    -> R.string.isnotavailable
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
