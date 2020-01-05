package info.nightscout.androidaps.plugins.general.automation.elements

import android.widget.LinearLayout
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

abstract class Element(val mainApp: MainApp) {
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var configBuilderPlugin: ConfigBuilderPlugin

    abstract fun addToLayout(root: LinearLayout)

    init {
        mainApp.androidInjector().inject(this)
    }
}