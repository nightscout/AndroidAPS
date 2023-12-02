package app.aaps.plugins.configuration.setupwizard

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.plugins.configuration.setupwizard.elements.SWItem
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class SWScreen(val injector: HasAndroidInjector, private var header: Int) {

    @Inject lateinit var rh: ResourceHelper

    var items: MutableList<SWItem> = ArrayList()
    var validator: (() -> Boolean)? = null
    var visibility: (() -> Boolean)? = null
    var skippable = false

    init {
        injector.androidInjector().inject(this)
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

    fun processVisibility() {
        for (i in items) i.processVisibility()
    }
}