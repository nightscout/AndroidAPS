package app.aaps.plugins.configuration.setupwizard

import androidx.appcompat.app.AppCompatActivity
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.plugins.configuration.setupwizard.elements.SWItem
import javax.inject.Inject

class SWScreen @Inject constructor(private val rh: ResourceHelper) {

    private var header: Int = 0

    var items: MutableList<SWItem> = ArrayList()
    var validator: (() -> Boolean)? = null
    var visibility: (() -> Boolean)? = null
    var skippable = false

    fun with(header: Int): SWScreen {
        this.header = header
        return this
    }

    fun getHeader(): String {
        return rh.gs(header)
    }

    fun skippable(skippable: Boolean): SWScreen {
        this.skippable = skippable
        return this
    }

    fun add(newItem: SWItem): SWScreen {
        items.add(newItem)
        return this
    }

    fun validator(validator: () -> Boolean): SWScreen {
        this.validator = validator
        return this
    }

    fun visibility(visibility: () -> Boolean): SWScreen {
        this.visibility = visibility
        return this
    }

    fun processVisibility(activity: AppCompatActivity) {
        for (i in items) i.processVisibility(activity)
    }
}