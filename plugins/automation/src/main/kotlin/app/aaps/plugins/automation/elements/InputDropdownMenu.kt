package app.aaps.plugins.automation.elements

import app.aaps.core.interfaces.resources.ResourceHelper

class InputDropdownMenu(private val rh: ResourceHelper) {

    private var itemList: ArrayList<CharSequence> = ArrayList()
    var value: String = ""

    constructor(rh: ResourceHelper, name: String) : this(rh) {
        value = name
    }

    @Suppress("unused")
    constructor(rh: ResourceHelper, another: InputDropdownMenu) : this(rh) {
        value = another.value
    }

    fun setValue(name: String): InputDropdownMenu {
        value = name
        return this
    }

    fun setList(values: ArrayList<CharSequence>) {
        itemList = ArrayList(values)
    }

    // For testing only
    fun add(item: String) {
        itemList.add(item)
    }
}
